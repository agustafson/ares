package redscaler.interpreter

import cats._
import cats.instances.function._
import cats.syntax.tuple._
import cats.syntax.functor._
import cats.syntax.flatMap._
import com.typesafe.scalalogging.StrictLogging
import fs2.{Handle, Pull}
import redscaler._

import scala.annotation.tailrec

trait RedisResponseHandler[F[_]] extends StrictLogging {

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

  implicit val byteHandlePullMonad = new Monad[λ[α => ByteHandle => ByteHandlePull[α]]] {
    override def pure[A](a: A): ByteHandle => ByteHandlePull[A] = handle => Pull.pure[(A, ByteHandle)]((a, handle))

    override def flatMap[A, B](fa: (ByteHandle) => ByteHandlePull[A])(
        f: (A) => (ByteHandle) => ByteHandlePull[B]): (ByteHandle) => ByteHandlePull[B] = {
      fa.andThen(_.flatMap {
        case (a, nextHandle) => f(a)(nextHandle)
      })
    }

    override def tailRecM[A, B](a: A)(
        f: (A) => (ByteHandle) => ByteHandlePull[Either[A, B]]): (ByteHandle) => ByteHandlePull[B] = {
      f(a).andThen {
        _.flatMap[F, Nothing, (B, ByteHandle)] {
          case (Left(a1), nextHandle) =>
            tailRecM(a1)(f)(nextHandle)
          case (Right(b), nextHandle) =>
            Pull.pure((b, nextHandle))
        }
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

  private val processSimpleStringReply: ByteHandle => ByteHandlePull[SimpleStringReply] =
    takeLine.andThen(_.mapLeft(bytes => SimpleStringReply(bytes.asString)))

  private val processBulkReply: ByteHandle => ByteHandlePull[BulkReply] = { h =>
    for {
      (messageLength, h) <- takeIntegerLine(h)
      result <- if (messageLength == -1) Pull.pure((BulkReply(None), h))
      else takeContentByLengthAndNewLine(messageLength.toInt).andThen(_.mapLeft(bytes => BulkReply(Some(bytes))))(h)
    } yield {
      result
    }
  }

  private val processMultiBulkReply: ByteHandle => ByteHandlePull[ArrayReply] = {
    @tailrec
    def takeReplies(remainingCount: Int,
                    pull: ByteHandlePull[List[RedisResponse]]): ByteHandlePull[List[RedisResponse]] = {
      if (remainingCount > 0) {
        /*
        val newState = currentState.transform {
          case (currentBytes, responses) =>
            val (remainingBytes, reply) = handleSingleResult.run(currentBytes)
            (remainingBytes, responses :+ reply)
        }
         */
        takeReplies(remainingCount - 1, for {
          (responses, handle) <- pull
          (response, handle)  <- handleSingleResult(handle)
        } yield (responses :+ response, handle))
      } else pull
    }

    takeIntegerLine.andThen(_.flatMap {
      case (numberOfArgs, handle) =>
        takeReplies(numberOfArgs.toInt, Pull.pure((List.empty, handle))).mapLeft(replies => ArrayReply(replies))
    })
  }

  private def processInteger: ByteHandle => ByteHandlePull[IntegerReply] =
    takeIntegerLine.andThen(_.mapLeft(IntegerReply))

  private def processError: ByteHandle => ByteHandlePull[ErrorReply] =
    takeLine.andThen(_.mapLeft(vector => ErrorReply(vector.asString)))

  def handleSingleResult: ByteHandle => ByteHandlePull[RedisResponse] = { handle =>
    handle.receive1 {
      case (b, h) =>
        b match {
          case PLUS_BYTE =>
            processSimpleStringReply(h)
          case DOLLAR_BYTE =>
            processBulkReply(h)
          case ASTERISK_BYTE =>
            processMultiBulkReply(h)
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
