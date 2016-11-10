package ares.interpreter

import ares._
import cats._
import cats.data.StateT
import com.typesafe.scalalogging.StrictLogging

object RedisResponseHandler extends StrictLogging {

  import RedisConstants._

  type ByteVectorState[A] = StateT[Id, Vector[Byte], A]

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

  private val takeLine: ByteVectorState[Vector[Byte]] =
    StateT[Id, Vector[Byte], Vector[Byte]] { bytes =>
      val crlfIndex                   = bytes.indexOfSlice(CRLF)
      val (firstLine, remainingBytes) = bytes.splitAt(crlfIndex)
      (remainingBytes.drop(2), firstLine)
    }

  private val getIntegerLine: ByteVectorState[Long] =
    takeLine.map(bytes => bytes.map(_.toChar).mkString.toLong)

}
