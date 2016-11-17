package ares.interpreter

import java.nio.channels.AsynchronousChannelGroup

import ares._
import ares.interpreter.ArgConverters.stringArgConverter
import cats.Functor
import cats.syntax.functor._
import com.typesafe.scalalogging.StrictLogging
import fs2.Stream
import fs2.io.tcp.Socket
import fs2.util.Async

class Fs2CommandInterpreter[F[_]: Functor](redisClient: Stream[F, Socket[F]])(implicit asyncM: Async[F],
                                                                              tcpACG: AsynchronousChannelGroup)
    extends BaseFs2Interpreter[F](redisClient)
    with RedisCommands.Interp[F]
    with StrictLogging {

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
