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
import com.typesafe.scalalogging.StrictLogging
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
  extends RedisCommands.Interp[Task] with StrictLogging {

  import Task.asyncInstance
  import RedisConstants._

  override def get(key: String): Task[Option[String]] = {
    sendCommand(createCommand("GET", key)).map {
      case reply: BulkReply =>
        logger.debug(s"the get reply is: $reply")
        reply.body.map(_.asString)
      case error: ErrorReply =>
        Some(error.errorMessage)
      case unknownReply => throw new RuntimeException("boom")
    }
  }

  override def set(key: String, value: String): Task[Either[ErrorReply, Unit]] = {
    sendCommand(createCommand("SET", key, value)).map {
      case SimpleStringReply("OK") => Right(())
      case errorReply: ErrorReply => Left(errorReply)
      case unknownReply => throw new RuntimeException("boom")
    }
  }

  private lazy val client: Stream[Task, Socket[Task]] = tcp.client[Task](redisHost, reuseAddress = true, keepAlive = true, noDelay = true)

  private def sendCommand(chunk: Chunk[Byte]): Task[RedisResponse] = {
    logger.debug(s"sending command $chunk")

    val writeAndRead: (Socket[Task]) => Stream[Task, Vector[Byte]] = { socket =>
      Stream.chunk(chunk).to(socket.writes(Some(2.seconds))).drain.onFinalize(socket.endOfOutput) ++
        socket.reads(1024, Some(2.seconds)).chunks.map(_.toVector)
    }
    client.flatMap(writeAndRead).runFold(Vector.empty[Byte])(_ ++ _).map(RedisResponseHandler.handleResponse)
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

    logger.debug(s"command created: ${bytes.result().toVector.asString}")

    Chunk.bytes(bytes.result().toArray)
  }
}

object RedisResponseHandler extends StrictLogging {
  import RedisConstants._

  type ByteVectorState[A] = StateT[Id, Vector[Byte], A]

  sealed trait RedisResponse
  case class SimpleStringReply(body: String) extends RedisResponse
  case class IntegerReply(long: Long) extends RedisResponse
  case class BulkReply(body: Option[Vector[Byte]]) extends RedisResponse
  case class ErrorReply(errorMessage: String) extends RedisResponse

  def handleResponse(bytes: Vector[Byte]): RedisResponse = {
    logger.debug(s"handle response: ${bytes.asString}")
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
    logger.debug(s"response: $result")
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
    ErrorReply(takeLine.runA(bytes).asString)
  }


  private val takeLine: ByteVectorState[Vector[Byte]] = StateT[Id, Vector[Byte], Vector[Byte]] { bytes =>
    val crlfIndex = bytes.indexOfSlice(CRLF)
    val (firstLine, remainingBytes) = bytes.splitAt(crlfIndex)
    (remainingBytes.drop(2), firstLine)
  }

  private val getIntegerLine: ByteVectorState[Long] =
    takeLine.map(bytes => bytes.map(_.toChar).mkString.toLong)
}