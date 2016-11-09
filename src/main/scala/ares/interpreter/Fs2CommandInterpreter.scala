package ares.interpreter

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup

import ares._
import cats.Functor
import com.typesafe.scalalogging.StrictLogging
import fs2.util.Async

class Fs2CommandInterpreter[F[_]: Functor](redisHost: InetSocketAddress)(implicit asyncM: Async[F],
                                                                         tcpACG: AsynchronousChannelGroup)
    extends BaseFs2Interpreter[F](redisHost)
    with RedisCommands.Interp[F]
    with StrictLogging {

  override def get(key: String): F[Option[String]] = {
    runCommand(createCommand("GET", key)) {
      case reply: BulkReply =>
        logger.debug(s"the get reply is: $reply")
        reply.body.map(_.asString)
      case error: ErrorReply =>
        Some(error.errorMessage)
      case unknownReply => throw new RuntimeException("boom")
    }
  }

  override def set(key: String, value: String): F[Either[ErrorReply, Unit]] = {
    runCommand(createCommand("SET", key, value)) {
      case SimpleStringReply("OK") => Right(())
      case errorReply: ErrorReply  => Left(errorReply)
      case unknownReply            => throw new RuntimeException("boom")
    }
  }

}
