package redscaler.interpreter

import com.typesafe.scalalogging.StrictLogging
import fs2.Stream
import fs2.io.tcp.Socket
import fs2.util.syntax._
import fs2.util.{Applicative, Catchable}
import redscaler._
import redscaler.interpreter.ArgConverters._

class Fs2CommandInterpreter[F[_]: Applicative: Catchable](redisClient: Stream[F, Socket[F]])
    extends CommandExecutor[F](redisClient)
    with RedisCommands.Interp[F]
    with StrictLogging {

  type Result[A] = F[ErrorReplyOr[A]]

  override def selectDatabase(databaseIndex: Int): Result[Unit] = {
    logger.info(s"Selecting database $databaseIndex")

    runCommand(s"select $databaseIndex").map(handleReplyWithErrorHandling {
      case SimpleStringReply("OK") => ()
    })
  }

  override def flushdb: Result[Unit] = {
    logger.info(s"Flushing db")

    runCommand("flushdb").map(handleReplyWithErrorHandling {
      case SimpleStringReply("OK") => ()
    })
  }

  override def get(key: String): Result[Option[Vector[Byte]]] = {
    runCommand("GET", key).map(handleReplyWithErrorHandling {
      case BulkReply(body) => body
    })
  }

  override def set(key: String, value: Vector[Byte]): Result[Unit] = {
    runCommand("SET", key, value).map(handleReplyWithErrorHandling {
      case SimpleStringReply("OK") => ()
    })
  }

  override def lpush(key: String, value: String): Result[Int] = {
    runCommand("LPUSH", key, value).map(handleReplyWithErrorHandling {
      case IntegerReply(count) => count.toInt
    })
  }

  override def rpush(key: String, value: String): Result[Int] = {
    runCommand("RPUSH", key, value).map(handleReplyWithErrorHandling {
      case IntegerReply(count) => count.toInt
    })
  }

  override def lrange(key: String, startIndex: Int, endIndex: Int): Result[List[String]] = {
    runCommand("LRANGE", key, startIndex, endIndex).map(handleReplyWithErrorHandling {
      case replies: ArrayReply =>
        replies.replies.collect {
          case BulkReply(bodyMaybe) => bodyMaybe.getOrElse(Vector.empty).asString
        }
    })
  }

  private def handleReplyWithErrorHandling[A](
      handler: PartialFunction[RedisResponse, A]): PartialFunction[RedisResponse, ErrorReplyOr[A]] = {
    handler.andThen(Right[ErrorReply, A]).orElse {
      case errorReply: ErrorReply => Left[ErrorReply, A](errorReply)
      case unknownReply           => throw new RuntimeException("boom")
    }
  }
}
