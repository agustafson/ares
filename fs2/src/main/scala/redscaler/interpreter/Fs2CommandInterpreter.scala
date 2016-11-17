package redscaler.interpreter

import com.typesafe.scalalogging.StrictLogging
import fs2.Stream
import fs2.io.tcp.Socket
import fs2.util.syntax._
import fs2.util.{Applicative, Catchable}
import redscaler._
import redscaler.interpreter.ArgConverters.stringArgConverter

class Fs2CommandInterpreter[F[_]: Applicative: Catchable](redisClient: Stream[F, Socket[F]])
    extends CommandExecutor[F](redisClient)
    with RedisCommands.Interp[F]
    with StrictLogging {

  override def selectDatabase(databaseIndex: Int): F[Either[ErrorReply, Unit]] = {
    logger.info(s"Selecting database $databaseIndex")

    runCommand(s"select $databaseIndex").map {
      case SimpleStringReply("OK") => Right(())
      case errorReply: ErrorReply  => Left(errorReply)
      case unknownReply            => throw new RuntimeException("boom")
    }
  }

  override def flushdb: F[ErrorReplyOrUnit] = {
    logger.info(s"Flushing db")

    runCommand("flushdb").map {
      case SimpleStringReply("OK") => Right(())
      case errorReply: ErrorReply  => Left(errorReply)
      case unknownReply            => throw new RuntimeException("boom")
    }
  }

  override def get(key: String): F[Option[Vector[Byte]]] = {
    runCommand("GET", key).map {
      case reply: BulkReply =>
        logger.debug(s"the get reply is: $reply")
        reply.body
      case error: ErrorReply =>
        Some(error.errorMessage)
      case unknownReply => throw new RuntimeException("boom")
    }
  }

  override def set(key: String, value: Vector[Byte]): F[Either[ErrorReply, Unit]] = {
    runCommand("SET", key, value).map {
      case SimpleStringReply("OK") => Right(())
      case errorReply: ErrorReply  => Left(errorReply)
      case unknownReply            => throw new RuntimeException("boom")
    }
  }

}
