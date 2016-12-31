package redscaler

import java.net.{InetSocketAddress, StandardSocketOptions}
import java.nio.ByteBuffer
import java.nio.channels.spi.AsynchronousChannelProvider
import java.nio.channels.{AsynchronousChannelGroup, CompletionHandler}
import java.util.concurrent.{ExecutorService, Executors}

import fs2.Strategy
import redscaler.interpreter.ArgConverters._
import redscaler.interpreter.RedisConstants._
import redscaler.interpreter._

import scala.collection.mutable

object ReaderASG extends App {

  def createCommand(command: String, args: Seq[Vector[Byte]]): ByteBuffer = {
    val bytes = new mutable.ListBuffer() +=
        ASTERISK_BYTE ++= intCrlf(args.length + 1) +=
        DOLLAR_BYTE ++= intCrlf(command.length) ++=
        command.toArray.map(_.toByte) ++= CRLF ++=
        args.flatMap(arg => (DOLLAR_BYTE +: intCrlf(arg.length)) ++ arg ++ CRLF)

    println(s"command created: ${bytes.result().toVector.asString}")

    ByteBuffer.wrap(bytes.result().toArray)
  }

  val executor: ExecutorService = Executors.newFixedThreadPool(8, Strategy.daemonThreadFactory("reader"))
  val acg                       = AsynchronousChannelGroup.withThreadPool(executor)

  val ch = AsynchronousChannelProvider.provider().openAsynchronousSocketChannel(acg)
  ch.setOption[java.lang.Boolean](StandardSocketOptions.SO_REUSEADDR, true)
  ch.setOption[Integer](StandardSocketOptions.SO_SNDBUF, 8)
  ch.setOption[Integer](StandardSocketOptions.SO_RCVBUF, 8)
  ch.setOption[java.lang.Boolean](StandardSocketOptions.SO_KEEPALIVE, true)
  ch.setOption[java.lang.Boolean](StandardSocketOptions.TCP_NODELAY, true)

  ch.connect(
    new InetSocketAddress("127.0.0.1", 6379),
    null,
    new CompletionHandler[Void, Void] {
      override def completed(result: Void, attachment: Void): Unit = {
        println(s"result is: $result")
      }

      override def failed(ex: Throwable, attachment: Void): Unit = {
        ex.printStackTrace()
      }
    }
  )

  val writeResult = ch.write(createCommand("SUBSCRIBE", Seq("ch1", "ch2"))).get()
  println(s"write result: $writeResult")

  //val resultBuffer = new mutable.ListBuffer[Byte]()
  val results = ByteBuffer.allocate(256)

  while (results.hasRemaining) {
    ch.read(results).get()
  }

  println(s"final results: ${results.array().mkString}")

}
