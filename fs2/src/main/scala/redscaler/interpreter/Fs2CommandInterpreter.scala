package redscaler.interpreter

import cats.data.NonEmptyList
import com.typesafe.scalalogging.StrictLogging
import fs2.util.syntax._
import fs2.util.{Applicative, Catchable}
import redscaler._
import redscaler.ConnectionOps.ops
import redscaler.interpreter.ArgConverters._
import redscaler.interpreter.ResponseHandler.handleResponseWithErrorHandling

class Fs2CommandInterpreter[F[_]: Applicative: Catchable](connection: Connection[F])
    extends ConnectionOps.Interp[F]
    with StrictLogging {

  import connection._

  type Result[A] = F[ErrorOr[A]]

  override def selectDatabase(databaseIndex: Int): Result[Unit] = {
    logger.info(s"Selecting database $databaseIndex")
    execute(ops.selectDatabase(databaseIndex))
//    execute(Command.keyCommand(s"SELECT", databaseIndex.toString, Seq.empty)).map(handleOkResponse)
  }

  override def flushdb: Result[Unit] = {
    logger.info(s"Flushing db")
    execute(Command.noArgCommand("flushdb")).map(handleOkResponse)
  }

  override def get(key: String): Result[Option[Vector[Byte]]] =
    execute(Command.keyCommand("GET", key, Seq.empty)).map(handleBulkResponse)

  override def set(key: String, value: Vector[Byte]): Result[Unit] =
    execute(Command.keyCommand("SET", key, Seq(value))).map(handleOkResponse)

  override def getset(key: String, value: Vector[Byte]): F[ErrorOr[Option[Vector[Byte]]]] =
    execute(Command.keyCommand("GETSET", key, Seq(value))).map(handleBulkResponse)

  override def append(key: String, value: Vector[Byte]): F[ErrorOr[Int]] =
    execute(Command.keyCommand("APPEND", key, Seq(value))).map(handleIntResponse)

  // List commands
  override def lpush(key: String, values: NonEmptyList[Vector[Byte]]): Result[Int] =
    execute(Command.keyCommand("LPUSH", key, values.toList)).map(handleIntResponse)

  override def lpushx(key: String, values: NonEmptyList[Vector[Byte]]): Result[Int] =
    execute(Command.keyCommand("LPUSHX", key, values.toList)).map(handleIntResponse)

  override def rpush(key: String, values: NonEmptyList[Vector[Byte]]): Result[Int] =
    execute(Command.keyCommand("RPUSH", key, values.toList)).map(handleIntResponse)

  override def rpushx(key: String, values: NonEmptyList[Vector[Byte]]): Result[Int] =
    execute(Command.keyCommand("RPUSHX", key, values.toList)).map(handleIntResponse)

  override def lrange(key: String, startIndex: Int, endIndex: Int): Result[List[Vector[Byte]]] = {
    execute(Command.keyCommand("LRANGE", key, Seq(startIndex, endIndex))).map(handleResponseWithErrorHandling {
      case replies: ArrayResponse =>
        replies.replies.collect {
          case BulkResponse(bodyMaybe) => bodyMaybe.getOrElse(Vector.empty)
        }
    })
  }

  private def handleOkResponse: ErrorOr[RedisResponse] => ErrorOr[Unit] =
    handleResponseWithErrorHandling {
      case SimpleStringResponse("OK") => ()
    }

  private def handleIntResponse: ErrorOr[RedisResponse] => ErrorOr[Int] =
    handleResponseWithErrorHandling {
      case IntegerResponse(num) => num.toInt
    }

  private def handleBulkResponse: ErrorOr[RedisResponse] => ErrorOr[Option[Vector[Byte]]] =
    handleResponseWithErrorHandling {
      case BulkResponse(body) => body
    }

}
