package redscaler.interpreter

import cats._
import cats.data.StateT
import com.typesafe.scalalogging.StrictLogging
import redscaler._

import scala.annotation.tailrec

object RedisResponseHandler extends StrictLogging {

  import RedisConstants._

  type ByteVectorState[A] = StateT[Id, Vector[Byte], A]
  object ByteVectorState {
    def apply[A](f: Vector[Byte] => (Vector[Byte], A)) = StateT.apply[Id, Vector[Byte], A](f)
  }

  def handleResponse(bytes: Vector[Byte]): RedisResponse = {
    logger.debug(s"handle response: ${bytes.asString}")
    val result = handleSingleResult.runA(bytes)
    logger.debug(s"response: $result")
    result
  }

  private val takeLine: ByteVectorState[Vector[Byte]] = ByteVectorState { bytes =>
    takeContentByLengthAndNewLine(bytes.indexOfSlice(CRLF)).run(bytes)
  }

  private def takeContentByLengthAndNewLine(contentLength: Int): ByteVectorState[Vector[Byte]] =
    ByteVectorState { bytes =>
      val (firstLine, remainingBytes) = bytes.splitAt(contentLength)
      (remainingBytes.drop(2), firstLine)
    }

  private val takeIntegerLine: ByteVectorState[Long] =
    takeLine.map(bytes => bytes.map(_.toChar).mkString.toLong)

  private val processSimpleStringReply: ByteVectorState[SimpleStringReply] =
    takeLine.map(bytes => SimpleStringReply(bytes.asString))

  private val processBulkReply: ByteVectorState[BulkReply] = {
    takeIntegerLine.transform {
      case (remainingBytes, messageLength) =>
        val (newBytes, reply) =
          if (messageLength == -1)
            (remainingBytes, None)
          else {
            takeContentByLengthAndNewLine(messageLength.toInt).map(Some(_)).run(remainingBytes)
          }
        newBytes -> BulkReply(reply)
    }
  }

  private val processMultiBulkReply: ByteVectorState[ArrayReply] = {
    @tailrec
    def takeReplies(remainingCount: Int,
                    currentState: ByteVectorState[List[RedisResponse]]): ByteVectorState[List[RedisResponse]] = {
      if (remainingCount > 0) {
        val newState = currentState.transform {
          case (currentBytes, responses) =>
            val (remainingBytes, reply) = handleSingleResult.run(currentBytes)
            (remainingBytes, responses :+ reply)
        }
        takeReplies(remainingCount - 1, newState)
      } else currentState
    }

    takeIntegerLine.flatMap { numberOfArgs =>
      takeReplies(numberOfArgs.toInt, ByteVectorState { bytes =>
        (bytes, List.empty[RedisResponse])
      }).map(replies => ArrayReply(replies))
    }
  }

  private def processInteger: ByteVectorState[IntegerReply] = takeIntegerLine.map(IntegerReply)

  private def processError: ByteVectorState[ErrorReply] = takeLine.map(vector => ErrorReply(vector.asString))

  private def handleSingleResult: ByteVectorState[RedisResponse] = ByteVectorState { bytes =>
    val messageBytes = bytes.tail
    val reply: (Vector[Byte], RedisResponse) = bytes.head match {
      case PLUS_BYTE =>
        processSimpleStringReply.run(messageBytes)
      case DOLLAR_BYTE =>
        processBulkReply.run(messageBytes)
      case ASTERISK_BYTE =>
        processMultiBulkReply.run(messageBytes)
      case COLON_BYTE =>
        processInteger.run(messageBytes)
      case MINUS_BYTE =>
        processError.run(messageBytes)
      case b =>
        throw new RuntimeException("Unknown reply: " + b.toChar)
    }
    reply
  }
}
