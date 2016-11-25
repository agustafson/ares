package redscaler.interpreter

import com.typesafe.scalalogging.StrictLogging
import fs2.io.tcp.Socket
import fs2.util.syntax._
import fs2.util.{Applicative, Catchable}
import fs2.{Chunk, Stream}
import redscaler.RedisResponse
import redscaler.interpreter.ArgConverters.stringArgConverter
import redscaler.interpreter.RedisConstants._

import scala.collection.mutable
import scala.concurrent.duration._

abstract class CommandExecutor[F[_]: Applicative: Catchable](redisClient: Stream[F, Socket[F]]) extends StrictLogging {

  val writeTimeout = Some(2.seconds)
  val readTimeout  = Some(2.seconds)
  val maxBytesRead = 1024

  protected def runKeyCommand(command: String, key: String, args: Vector[Byte]*): F[RedisResponse] = {
    runCommand(command, stringArgConverter(key) +: args)
  }

  protected def runNoArgCommand(command: String): F[RedisResponse] = {
    runCommand(command, Seq.empty)
  }

  protected def runCommand[T](command: String, args: Seq[Vector[Byte]]): F[RedisResponse] = {
    sendCommand(createCommand(command, args))
  }

  protected def streamCommand(command: String, args: Seq[Vector[Byte]]): Stream[F, RedisResponse] = {
    streamCommandResults(createCommand(command, args)).map(RedisResponseHandler.handleResponse)
  }

  private def createCommand(command: String, args: Seq[Vector[Byte]]): Chunk[Byte] = {
    val bytes = new mutable.ListBuffer() +=
        ASTERISK_BYTE ++= intCrlf(args.length + 1) +=
        DOLLAR_BYTE ++= intCrlf(command.length) ++=
        command.toArray.map(_.toByte) ++= CRLF ++=
        args.flatMap(arg => (DOLLAR_BYTE +: intCrlf(arg.length)) ++ arg ++ CRLF)

§    logger.trace(s"command created: ${bytes.result().toVector.asString}")

    Chunk.bytes(bytes.result().toArray)
  }

  private def sendCommand(chunk: Chunk[Byte]): F[RedisResponse] = {
    streamCommandResults(chunk).runFold(Vector.empty[Byte])(_ ++ _).map(RedisResponseHandler.handleResponse)
  }

  private def streamCommandResults(chunk: Chunk[Byte]): Stream[F, Vector[Byte]] = {
    logger.trace(s"sending command $chunk")

    val writeAndRead: (Socket[F]) => Stream[F, Vector[Byte]] = { socket =>
      Stream.chunk(chunk).to(socket.writes(writeTimeout)).drain.onFinalize(socket.endOfOutput) ++
        socket.reads(maxBytesRead, readTimeout).chunks.map(_.toVector)
    }
    val stream: Stream[F, Vector[Byte]] = redisClient.flatMap(writeAndRead)
    stream
  }
}
