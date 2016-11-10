package ares.interpreter

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup

import ares.RedisCommands.Command
import ares.RedisCommands.Command.Set
import ares._
import ares.interpreter.ArgConverters.stringArgConverter
import cats.Functor
import com.typesafe.scalalogging.StrictLogging
import fs2.Chunk
import fs2.util.Async

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

class Fs2CommandInterpreter[F[_]: Functor](redisHost: InetSocketAddress)(implicit asyncM: Async[F],
                                                                         tcpACG: AsynchronousChannelGroup)
    extends BaseFs2Interpreter[F](redisHost)
    with RedisCommands.Interp[F]
    with StrictLogging {

  override def get(key: String): F[Option[Vector[Byte]]] = {
    runCommand(createCommand("GET", key)) {
      case reply: BulkReply =>
        logger.debug(s"the get reply is: $reply")
        reply.body
      case error: ErrorReply =>
        Some(error.errorMessage)
      case unknownReply => throw new RuntimeException("boom")
    }
  }

  override def set(key: String, value: Vector[Byte]): F[Either[ErrorReply, Unit]] = {
    val commandWriter: CommandWriter[Set] = CommandWriter.commandWriterForType[Set]
    val command: Set                      = Command.Set(key, value)
    val chunk: Chunk[Byte]                = Chunk.bytes(commandWriter.asCommand(command))
//    val chunk: Chunk[Byte] = commandToChunk[Set](command)
    runCommand(chunk) {
      case SimpleStringReply("OK") => Right(())
      case errorReply: ErrorReply  => Left(errorReply)
      case unknownReply            => throw new RuntimeException("boom")
    }
  }

  def commandToChunk[C <: Command[_]: TypeTag](op: C) = {
    val commandWriter = CommandWriter.commandWriterForType[C]
    Chunk.bytes(commandWriter.asCommand(op))
  }

  private def getOpName[T, C <: DatabaseCommands.DatabaseCommand[_]](op: C): String = {
    val className: String = op.getClass.getName
    className.substring(className.lastIndexOf('$') + 1).toLowerCase
  }

}
