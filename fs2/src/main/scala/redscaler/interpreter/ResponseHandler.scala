package redscaler.interpreter

import com.typesafe.scalalogging.StrictLogging
import fs2.{Handle, Pull}
import redscaler._

import scala.annotation.tailrec

trait ResponseHandler[F[_]] extends StrictLogging {

  import RedisConstants._

  private type ByteHandle        = Handle[F, Byte]
  private type ByteHandlePull[A] = Pull[F, Nothing, (A, ByteHandle)]

  /*
  private type PullHandler[A]    = ByteHandle => ByteHandlePull[A]

  implicit val byteHandlePullFunctor: Monad[PullHandler] = new Monad[PullHandler] {

//    override def map[A, B](fa: PullHandler[A])(f: (A) => B): PullHandler[B] = {
//      fa.andThen(_.map {
//        case (a, handle) => (f(a), handle)
//      })
//    }
    override def pure[A](a: A): PullHandler[A] = _ => Pull.pure[(A, ByteHandle)]((a, Handle.empty[F, Byte]))

    override def flatMap[A, B](fa: PullHandler[A])(f: A => PullHandler[B]): PullHandler[B] = { handle =>
      //fa(handle).flatMap()
      ???
    }

    override def tailRecM[A, B](a: A)(f: (A) => PullHandler[Either[A, B]]): PullHandler[B] = ???
  }
   */

  implicit class ByteHandlePullW[A](underlying: ByteHandlePull[A]) {
    def mapLeft[B](f: A => B): ByteHandlePull[B] = {
      underlying.map {
        case (a, handle) => (f(a), handle)
      }
    }
  }

  def handleResponse(byteHandle: ByteHandle): Pull[F, RedisResponse, ByteHandle] = {
    handleSingleResult(byteHandle).flatMap {
      case (response, handle) => Pull.output1[F, RedisResponse](response) as handle
    }
  }

  private val takeLine: ByteHandle => ByteHandlePull[Vector[Byte]] = {
    def takeByte(bytes: Vector[Byte]): ByteHandle => ByteHandlePull[Vector[Byte]] = { h =>
      if (bytes endsWith CRLF) {
        Pull.pure((bytes.dropRight(2), h))
      } else
        h.receive1 {
          case (b, tl) =>
            takeByte(bytes :+ b)(tl)
        }
    }
    takeByte(Vector.empty)
  }

  private def takeContentByLengthAndNewLine(contentLength: Int): ByteHandle => ByteHandlePull[Vector[Byte]] = { h =>
    for {
      (content, h) <- h.awaitN(contentLength, allowFewer = false)
      h            <- h.drop(2)
      result       <- Pull.pure((content.flatMap(_.toVector).toVector, h))
    } yield result
  }

  private val takeIntegerLine: ByteHandle => ByteHandlePull[Long] =
    takeLine.andThen(_.mapLeft(bytes => bytes.map(_.toChar).mkString.toLong))

  private val processSimpleStringResponse: ByteHandle => ByteHandlePull[SimpleStringResponse] =
    takeLine.andThen(_.mapLeft(bytes => SimpleStringResponse(bytes.asString)))

  private val processBulkResponse: ByteHandle => ByteHandlePull[BulkResponse] = { h =>
    for {
      (messageLength, h) <- takeIntegerLine(h)
      result <- if (messageLength == -1) Pull.pure((BulkResponse(None), h))
      else takeContentByLengthAndNewLine(messageLength.toInt).andThen(_.mapLeft(bytes => BulkResponse(Some(bytes))))(h)
    } yield {
      result
    }
  }

  private val processMultiBulkResponse: ByteHandle => ByteHandlePull[ArrayResponse] = {
    @tailrec
    def takeReplies(remainingCount: Int,
                    pull: ByteHandlePull[List[RedisResponse]]): ByteHandlePull[List[RedisResponse]] = {
      if (remainingCount > 0) {
        takeReplies(remainingCount - 1, for {
          (responses, handle) <- pull
          (response, handle)  <- handleSingleResult(handle)
        } yield (responses :+ response, handle))
      } else pull
    }

    takeIntegerLine.andThen(_.flatMap {
      case (numberOfArgs, handle) =>
        takeReplies(numberOfArgs.toInt, Pull.pure((List.empty, handle))).mapLeft(replies => ArrayResponse(replies))
    })
  }

  private def processInteger: ByteHandle => ByteHandlePull[IntegerResponse] =
    takeIntegerLine.andThen(_.mapLeft(IntegerResponse))

  private def processError: ByteHandle => ByteHandlePull[ErrorResponse] =
    takeLine.andThen(_.mapLeft(vector => ErrorResponse(vector.asString)))

  def handleSingleResult: ByteHandle => ByteHandlePull[RedisResponse] = { handle =>
    handle.receive1 {
      case (b, h) =>
        b match {
          case PLUS_BYTE =>
            processSimpleStringResponse(h)
          case DOLLAR_BYTE =>
            processBulkResponse(h)
          case ASTERISK_BYTE =>
            processMultiBulkResponse(h)
          case COLON_BYTE =>
            processInteger(h)
          case MINUS_BYTE =>
            processError(h)
          case b =>
            Pull.fail(UnexpectedResponseType(b.toChar))
        }
    }
  }
}
