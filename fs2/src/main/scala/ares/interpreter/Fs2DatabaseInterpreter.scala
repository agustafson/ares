package ares.interpreter

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup

import ares._
import cats.Functor
import cats.syntax.functor._
import com.typesafe.scalalogging.StrictLogging
import fs2.util.Async

class Fs2DatabaseInterpreter[F[_]: Functor](redisHost: InetSocketAddress)(implicit asyncM: Async[F],
                                                                          tcpACG: AsynchronousChannelGroup)
    extends BaseFs2Interpreter[F](redisHost)
    with DatabaseCommands.Interp[F]
    with StrictLogging {

  override def select(databaseIndex: Int): F[Either[ErrorReply, Unit]] = {
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

}
