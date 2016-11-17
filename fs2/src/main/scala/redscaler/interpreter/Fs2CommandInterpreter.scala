package redscaler.interpreter

import java.nio.channels.AsynchronousChannelGroup

import redscaler._
import redscaler.interpreter.ArgConverters.stringArgConverter
import redscaler.interpreter.RedisConstants._
import cats.Functor
import cats.syntax.functor._
import com.typesafe.scalalogging.StrictLogging
import fs2.io.tcp.Socket
import fs2.util.Async
import fs2.{Chunk, Stream}

import scala.collection.mutable
import scala.concurrent.duration._

class Fs2CommandInterpreter[F[_]: Functor](redisClient: Stream[F, Socket[F]])(implicit asyncM: Async[F],
                                                                              tcpACG: AsynchronousChannelGroup)
    extends RedisCommands.Interp[F]
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

  private def runCommand[T](command: String, args: Vector[Byte]*): F[RedisResponse] = {
    sendCommand(createCommand(command, args: _*))
  }

  private def createCommand(command: String, args: Vector[Byte]*): Chunk[Byte] = {
    val bytes = new mutable.ListBuffer() +=
        ASTERISK_BYTE ++= intCrlf(args.length + 1) +=
        DOLLAR_BYTE ++= intCrlf(command.length) ++=
        command.toArray.map(_.toByte) ++= CRLF ++=
        args.flatMap(arg => (DOLLAR_BYTE +: intCrlf(arg.length)) ++ arg ++ CRLF)

    logger.debug(s"command created: ${bytes.result().toVector.asString}")

    Chunk.bytes(bytes.result().toArray)
  }

  private def sendCommand(chunk: Chunk[Byte]): F[RedisResponse] = {
    logger.debug(s"sending command $chunk")

    val writeAndRead: (Socket[F]) => Stream[F, Vector[Byte]] = { socket =>
      Stream.chunk(chunk).to(socket.writes(Some(2.seconds))).drain.onFinalize(socket.endOfOutput) ++
        socket.reads(1024, Some(2.seconds)).chunks.map(_.toVector)
    }
    redisClient.flatMap(writeAndRead).runFold(Vector.empty[Byte])(_ ++ _).map(RedisResponseHandler.handleResponse)
  }

}
