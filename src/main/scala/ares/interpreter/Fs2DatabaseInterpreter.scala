package ares.interpreter

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup

import ares._
import cats.Functor
import com.typesafe.scalalogging.StrictLogging
import fs2.util.Async

class Fs2DatabaseInterpreter[F[_]: Functor](redisHost: InetSocketAddress)(implicit asyncM: Async[F],
                                                                          tcpACG: AsynchronousChannelGroup)
    extends BaseFs2Interpreter[F](redisHost)
    with DatabaseCommands.Interp[F]
    with StrictLogging {

  override def select(databaseIndex: Int): F[Either[ErrorReply, Unit]] = {
    logger.info(s"Selecting database $databaseIndex")

    runCommand(createCommand(s"select $databaseIndex")) {
      case SimpleStringReply("OK") => Right(())
      case errorReply: ErrorReply  => Left(errorReply)
      case unknownReply            => throw new RuntimeException("boom")
    }
  }

  override def flushdb: F[ErrorReplyOrUnit] = {
    logger.info(s"Flushing db")

    val commandName = getOpName(DatabaseCommands.DatabaseCommand.Flushdb())
    runCommand(createCommand(commandName)) {
      case SimpleStringReply("OK") => Right(())
      case errorReply: ErrorReply  => Left(errorReply)
      case unknownReply            => throw new RuntimeException("boom")
    }
  }

  private def getOpName[T, C <: DatabaseCommands.DatabaseCommand[_]](op: C): String = {
    val className: String = op.getClass.getName
    className.substring(className.lastIndexOf('$') + 1).toLowerCase
  }
}
