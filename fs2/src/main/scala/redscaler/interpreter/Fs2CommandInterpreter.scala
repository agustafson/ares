package redscaler.interpreter

import cats.data.NonEmptyList
import com.typesafe.scalalogging.StrictLogging
import fs2.Stream
import fs2.io.tcp.Socket
import fs2.util.syntax._
import fs2.util.{Applicative, Catchable}
import redscaler._
import redscaler.interpreter.ArgConverters._

class Fs2CommandInterpreter[F[_]: Applicative: Catchable](redisClient: Stream[F, Socket[F]])
    extends CommandExecutor[F](redisClient)
    with RedisCommands.Interp[F]
    with StrictLogging {

  type Result[A] = F[ErrorReplyOr[A]]

  override def selectDatabase(databaseIndex: Int): Result[Unit] = {
    logger.info(s"Selecting database $databaseIndex")
    runKeyCommand(s"SELECT", databaseIndex.toString).map(handleOkReply)
  }

  override def flushdb: Result[Unit] = {
    logger.info(s"Flushing db")
    runNoArgCommand("flushdb").map(handleOkReply)
  }

  override def get(key: String): Result[Option[Vector[Byte]]] = {
    runKeyCommand("GET", key).map(CommandExecutor.handleReplyWithErrorHandling {
      case BulkReply(body) => body
    })
  }

  override def set(key: String, value: Vector[Byte]): Result[Unit] = {
    runKeyCommand("SET", key, value).map(handleOkReply)
  }

  // List commands
  override def lpush(key: String, values: NonEmptyList[Vector[Byte]]): Result[Int] = {
    runKeyCommand("LPUSH", key, values.toList: _*).map(handleIntReply)
  }

  override def lpushx(key: String, values: NonEmptyList[Vector[Byte]]): Result[Int] = {
    runKeyCommand("LPUSHX", key, values.toList: _*).map(handleIntReply)
  }

  override def rpush(key: String, values: NonEmptyList[Vector[Byte]]): Result[Int] = {
    runKeyCommand("RPUSH", key, values.toList: _*).map(handleIntReply)
  }

  override def rpushx(key: String, values: NonEmptyList[Vector[Byte]]): Result[Int] = {
    runKeyCommand("RPUSHX", key, values.toList: _*).map(handleIntReply)
  }

  override def lrange(key: String, startIndex: Int, endIndex: Int): Result[List[Vector[Byte]]] = {
    runKeyCommand("LRANGE", key, startIndex, endIndex).map(CommandExecutor.handleReplyWithErrorHandling {
      case replies: ArrayReply =>
        replies.replies.collect {
          case BulkReply(bodyMaybe) => bodyMaybe.getOrElse(Vector.empty)
        }
    })
  }

  private def handleOkReply: RedisResponse => ErrorReplyOr[Unit] = CommandExecutor.handleReplyWithErrorHandling {
    case SimpleStringReply("OK") => ()
  }

  private def handleIntReply: RedisResponse => ErrorReplyOr[Int] = CommandExecutor.handleReplyWithErrorHandling {
    case IntegerReply(num) => num.toInt
  }

}
