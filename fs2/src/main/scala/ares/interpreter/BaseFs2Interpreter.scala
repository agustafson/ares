package ares.interpreter

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup

import ares.RedisResponse
import ares.interpreter.RedisConstants._
import cats.Functor
import cats.syntax.functor._
import com.typesafe.scalalogging.StrictLogging
import fs2.io.tcp.Socket
import fs2.util.Async
import fs2.{Chunk, Stream}

import scala.collection.mutable
import scala.concurrent.duration._

abstract class BaseFs2Interpreter[F[_]: Functor](redisClient: Stream[F, Socket[F]])(implicit asyncM: Async[F],
                                                                                    tcpACG: AsynchronousChannelGroup)
    extends StrictLogging {

  protected def runCommand[T](command: String, args: Vector[Byte]*): F[RedisResponse] = {
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
