package ares.interpreter

import java.lang.Thread.UncaughtExceptionHandler
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.spi.AsynchronousChannelProvider
import java.sql.ResultSet
import java.util.concurrent.{Executors, ThreadFactory}
import java.util.concurrent.atomic.AtomicInteger

import ares.RedisCommands
import ares.interpreter.RedisResponseHandler.{BulkReply, ErrorReply, RedisResponse, SimpleStringReply}
import cats.Id
import cats.data.StateT
import fs2.io.tcp
import fs2.io.tcp.Socket
import fs2.{Chunk, Strategy, Stream, Task}

import scala.collection.mutable
import scala.concurrent.duration._

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

class Fs2TaskInterpreter(redisHost: InetSocketAddress)(
  implicit tcpACG: AsynchronousChannelGroup,
  strategy: Strategy
)
  extends RedisCommands.Interp[Task] {

  import Task.asyncInstance
  import RedisConstants._

//  private val executor = Executors.newFixedThreadPool(8, Strategy.daemonThreadFactory("redis"))
//  implicit val strategy = Strategy.fromExecutor(executor)
//  import Task.asyncInstance
//  implicit val asg = AsynchronousChannelGroup.withCachedThreadPool(executor, 10)

  override def get(key: String): Task[Option[String]] = {
    val chunk = createCommand("GET", key)
    //(Stream.chunk(chunk))
    sendCommand(chunk).map {
      case reply: BulkReply =>
        println(s"the get reply is: $reply")
        reply.body.map(_.asString)
      case error: ErrorReply =>
        Some(error.errorMessage)
    }
  }

  override def set(key: String, value: String): Task[Either[ErrorReply, Unit]] = {
    val chunk = createCommand("SET", key, value)
    sendCommand(chunk).map { reply =>
      println(s"set reply is $reply")
      reply match {
        case SimpleStringReply("OK") => Right(Unit)
        case errorReply: ErrorReply => Left(errorReply)
        case unknownReply => throw new RuntimeException("boom")
      }
    }
  }

  //private lazy val client: Stream[Task, Socket[Task]] = tcp.client[Task](redisHost)

  private def sendCommand(chunk: Chunk[Byte]): Task[RedisResponse] = {
    println(s"sending command $chunk")

    /*
    def writeAndReadResponse(socket: Socket[Task]): Task[Vector[Byte]] = {
      for {
        w <- socket.write(chunk, Some(2.seconds))
        _ <- socket.endOfOutput
        _ = println(s"awaiting response: $w")
        r <- socket.reads(1024, Some(2.seconds)).runLog
        _ <- socket.endOfInput
        _ = println(s"result is: $r")
      } yield r
    }
    */

    /*
    val result: Stream[Task, Byte] = for {
      writeResult <- client.flatMap { socket =>
        println(s"writing")
        writeAndReadResponse(socket)
      }
    } yield result
    println("running thing")
    result.runLog.map(RedisResponseHandler.handleResponse)
    */

    /*client.mapAccumulate(Vector.empty[Byte]) {
      case (bytes, socket) =>
        writeAndReadResponse(socket).map(bytes1 => bytes ++ bytes1)
    }*/
    val redHost = tcp.client[Task](redisHost, reuseAddress = true, keepAlive = true, noDelay = true)
    println(s"redis host: $redHost")



    val writeAndRead: (Socket[Task]) => Stream[Task, Vector[Byte]] = { socket =>
      println("in client trying write")
      val streamChunk = Stream.chunk(chunk)
      println(s"create stream chunk: $streamChunk")
      val writtenToSocket = streamChunk.to(socket.writes(Some(2.seconds)))
      println("written to socket")
      val drained = writtenToSocket.drain
      println("drained")
      val writeResult = drained.onFinalize(socket.endOfOutput)
      println("end of output")
      val reads = socket.reads(1024, Some(2.seconds))
      println("read from socket")
      val readResult = reads.chunks.map(_.toVector)
      println("chunks mapped")
      writeResult ++ readResult
    }
    val result = redHost.flatMap(writeAndRead).runFold(Vector.empty[Byte])(_ ++ _).map(RedisResponseHandler.handleResponse)
    //val result = redHost.map(_ => RedisResponseHandler.ErrorReply("wibble")).runLog.map(_.last)
    println(s"got result: $result")
    result
    /*.runFold(Task.now(Vector.empty[Byte])) {
      case (bytesTask, nextBytesTask) =>
        for {
          a <- bytesTask
          b <- nextBytesTask
        } yield a ++ b
    }*/


    //client.map(_.write(chunk, Some(2.seconds))).runLog
  }

  private def intCrlf(i: Int): Vector[Byte] = i.toString.toVector.map(_.toByte) ++ CRLF

  private def createCommand(command: String, args: String*): Chunk[Byte] = {
    val bytes = new mutable.ListBuffer() +=
      ASTERISK_BYTE ++= intCrlf(args.length + 1) +=
      DOLLAR_BYTE ++= intCrlf(command.length) ++=
        command.toArray.map(_.toByte) ++= CRLF ++=
        args.flatMap { arg =>
          (DOLLAR_BYTE +: intCrlf(arg.length)) ++ arg.toArray.map(_.toByte) ++ CRLF
        }

    println(s"command created: ${bytes.result().toVector.asString}")

    Chunk.bytes(bytes.result().toArray)
  }
}

object RedisResponseHandler {
  import RedisConstants._

  type ByteVectorState[A] = StateT[Id, Vector[Byte], A]
  //def byteVectorState[A]: ByteVectorState[A] =

  sealed trait RedisResponse
  case class SimpleStringReply(body: String) extends RedisResponse
  case class IntegerReply(long: Long) extends RedisResponse
  case class BulkReply(body: Option[Vector[Byte]]) extends RedisResponse
  case class ErrorReply(errorMessage: String) extends RedisResponse

  def handleResponse(bytes: Vector[Byte]): RedisResponse = {
    println(s"handle response: ${bytes.asString}")
    val messageBytes = bytes.tail
    val result = bytes.head match {
      case PLUS_BYTE =>
        processSimpleStringReply(messageBytes)
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
    println(s"response: $result")
    result
  }

  private def processSimpleStringReply(bytes: Vector[Byte]): SimpleStringReply = {
    SimpleStringReply(takeLine.runA(bytes).asString)
  }

  private def processBulkReply(bytes: Vector[Byte]): BulkReply = {
    val (remainingBytes, messageLength) = getIntegerLine.run(bytes)
    val reply =
      if (messageLength == -1) None
      else Some(remainingBytes.take(messageLength.toInt))
    BulkReply(reply)
  }

  private def processMultiBulkReply(bytes: Vector[Byte]) = ???

  private def processInteger(bytes: Vector[Byte]): IntegerReply = {
    IntegerReply(getIntegerLine.runA(bytes))
  }

  private def processError(bytes: Vector[Byte]): ErrorReply = {
    val b = takeLine.runA(bytes)
    ErrorReply(b.asString)
  }


  private val takeLine: ByteVectorState[Vector[Byte]] = StateT[Id, Vector[Byte], Vector[Byte]] { bytes =>
    val crlfIndex = bytes.indexOfSlice(CRLF)
    val (firstLine, remainingBytes) = bytes.splitAt(crlfIndex)
    (remainingBytes.drop(2), firstLine)
  }

  private val getIntegerLine: ByteVectorState[Long] =
    takeLine.map(bytes => bytes.map(_.toChar).mkString.toLong)
}