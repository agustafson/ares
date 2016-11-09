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
    logger.info(s"using database $databaseIndex")

    runCommand(createCommand(s"select -1")) {
      case SimpleStringReply("OK") => Right(())
      case errorReply: ErrorReply  => Left(errorReply)
      case unknownReply            => throw new RuntimeException("boom")
    }
  }

}
