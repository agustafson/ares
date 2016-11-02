package ares.interpreter

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousChannelGroup
import java.nio.charset.Charset
import java.util.concurrent.{ExecutorService, Executors, ThreadPoolExecutor}

import ares.RedisCommands
import fs2.{Chunk, NonEmptyChunk, Strategy, Stream, Task}
import fs2.io.tcp
import fs2.io.tcp.Socket

import scala.annotation.tailrec
import scala.collection.mutable

object RedisConstants {
  val DOLLAR_BYTE: Byte = '$'
  val ASTERISK_BYTE: Byte = '*'
  val PLUS_BYTE: Byte = '+'
  val MINUS_BYTE: Byte = '-'
  val COLON_BYTE: Byte = ':'
  val CR: Byte = '\r'
  val LF: Byte = '\n'
  val CRLF: Vector[Byte] = Vector[Byte](CR, LF)
}

class Fs2TaskInterpreter extends RedisCommands.Interp[Task] {
  import RedisConstants._

  private val executor = Executors.newFixedThreadPool(8, Strategy.daemonThreadFactory("redis"))
  implicit val strategy = Strategy.fromExecutor(executor)
  import Task.asyncInstance
  implicit val asg = AsynchronousChannelGroup.withCachedThreadPool(executor, 10)

  override def get(key: String): Task[Option[String]] = {
    val chunk = createCommand("GET".getBytes, key.getBytes)
    //(Stream.chunk(chunk))
    ???
  }

  override def set(key: String, value: String): Task[Option[Boolean]] = ???



  private val client: Stream[Task, Socket[Task]] = tcp.client[Task](new InetSocketAddress("localhost", 6379))

  private def sendCommand(chunk: Chunk[Byte]) = {
    val result: Stream[Task, Byte] = for {
      _ <- client.map(_.write(chunk))
      result <- client.flatMap(_.reads(1024))
    } yield result
    result.runLog.map(RedisResponseHandler.handleResponse)
  }

  private def intCrlf(i: Int): Vector[Byte] = i.toByte +: CRLF

  private def createCommand(command: Array[Byte], args: Array[Byte]*): Chunk[Byte] = {
    val bytes = new mutable.ListBuffer() +=
      ASTERISK_BYTE ++= intCrlf(args.length + 1) +=
      DOLLAR_BYTE ++= intCrlf(command.length) ++=
        command ++= CRLF ++=
        args.flatMap { arg =>
          (DOLLAR_BYTE +: intCrlf(arg.length)) ++ arg ++ CRLF
        }

    Chunk.bytes(bytes.result().toArray)
  }
}

object RedisResponseHandler {
  import RedisConstants._

  sealed trait RedisResponse
  case class IntegerReply(long: Long) extends RedisResponse
  case class BulkReply(body: Vector[Byte]) extends RedisResponse

  def handleResponse(bytes: Vector[Byte]): RedisResponse = {
    val messageBytes = bytes.tail
    bytes.head match {
      case PLUS_BYTE =>
        processStatusCodeReply(messageBytes)
      case DOLLAR_BYTE =>
        processBulkReply(messageBytes)
      case ASTERISK_BYTE =>
        processMultiBulkReply(messageBytes)
      case COLON_BYTE =>
        processInteger(messageBytes)
      case MINUS_BYTE =>
        processError(messageBytes)
      case b =>
        throw new RuntimeException("Unknown reply: " + b.toChar)
    }
  }

  private def processStatusCodeReply(messageBytes: Vector[Byte]) = ???

  private def processBulkReply(messageBytes: Vector[Byte]): BulkReply = {
    println("full messageBytes: >>>")
    println(messageBytes)
    println("<<<")
    val (firstLine, remainder) = splitAtFirstNewLine(messageBytes)
    val firstLineString = firstLine.mkString
    println("firstline, remainder: >>>")
    println(firstLine)
    println(firstLineString)
    println(remainder)
    println("<<<")

    val messageLength = firstLineString.toInt
    println("remainder: >>>")
    println(remainder)
    println("<<<")
    val body = remainder.take(messageLength)
    val bodyString = asString(body/*, Charset.forName("UTF-16")*/)
    println(s"output message: length=$messageLength; body= >>>")
    println(body)
    println("<<<")
    BulkReply(body)
  }

  private def processMultiBulkReply(messageBytes: Vector[Byte]) = ???

  private def processInteger(messageBytes: Vector[Byte]): IntegerReply = {
    val (firstLine, _) = splitAtFirstNewLine(messageBytes)
    IntegerReply(asString(firstLine).toLong)
  }

  private def splitAtFirstNewLine(messageBytes: Vector[Byte]): (Vector[Byte], Vector[Byte]) = {
    @tailrec
    def findNextCrLf(bytes: Vector[Byte], currentIndex: Int): Int = {
      if (bytes.take(2) == CRLF) currentIndex
      else findNextCrLf(bytes.tail, currentIndex + 1)
    }
    val crlfIndex = findNextCrLf(messageBytes, 0)
    val (firstLine, remainder) = messageBytes.splitAt(crlfIndex)
    val drop = remainder.drop(2)
    println("rem: >>>")
    println(remainder)
    println("<<<")
    (firstLine, drop)
  }

  private def processError(messageBytes: Vector[Byte]) = ???

  private def asString(bytes: Vector[Byte]): String = {
    new String(bytes.toArray)
  }

}