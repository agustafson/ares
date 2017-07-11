package redscaler.interpreter

import cats.data.NonEmptyList
import com.typesafe.scalalogging.StrictLogging
import fs2.util.syntax._
import fs2.util.{Applicative, Catchable}
import redscaler._
import redscaler.interpreter.ArgConverters._
import redscaler.interpreter.ResponseHandler.handleResponseWithErrorHandling

class Fs2CommandInterpreter[F[_]: Applicative: Catchable](connection: Connection[F])
    extends ConnectionOps.Interp[F]
    with StrictLogging {

  import connection._

  type Result[A] = F[ErrorOr[A]]

  override def selectDatabase(databaseIndex: Int): Result[OkStringResponse.type] = {
    logger.info(s"Selecting database $databaseIndex")
    execute(Command.keyCommand(s"SELECT", databaseIndex.toString, Seq.empty)).map(handleOkStringResponse)
  }

  override def flushdb: Result[OkStringResponse.type] = {
    logger.info(s"Flushing db")
    execute(Command.noArgCommand("flushdb")).map(handleOkStringResponse)
  }

  override def get(key: String): Result[BulkResponse] =
    execute(Command.keyCommand("GET", key, Seq.empty)).map(handleBulkResponse)

  override def set(key: String, value: Vector[Byte]): Result[OkStringResponse.type] =
    execute(Command.keyCommand("SET", key, Seq(value))).map(handleOkStringResponse)

  override def getset(key: String, value: Vector[Byte]): F[ErrorOr[BulkResponse]] =
    execute(Command.keyCommand("GETSET", key, Seq(value))).map(handleBulkResponse)

  override def append(key: String, value: Vector[Byte]): F[ErrorOr[IntegerResponse]] =
    execute(Command.keyCommand("APPEND", key, Seq(value))).map(handleIntegerResponse)

  // List commands
  override def lpush(key: String, values: NonEmptyList[Vector[Byte]]): Result[IntegerResponse] =
    execute(Command.keyCommand("LPUSH", key, values.toList)).map(handleIntegerResponse)

  override def lpushx(key: String, values: NonEmptyList[Vector[Byte]]): Result[IntegerResponse] =
    execute(Command.keyCommand("LPUSHX", key, values.toList)).map(handleIntegerResponse)

  override def rpush(key: String, values: NonEmptyList[Vector[Byte]]): Result[IntegerResponse] =
    execute(Command.keyCommand("RPUSH", key, values.toList)).map(handleIntegerResponse)

  override def rpushx(key: String, values: NonEmptyList[Vector[Byte]]): Result[IntegerResponse] =
    execute(Command.keyCommand("RPUSHX", key, values.toList)).map(handleIntegerResponse)

  override def lrange(key: String, startIndex: Int, endIndex: Int): Result[ArrayResponse] = {
    execute(Command.keyCommand("LRANGE", key, Seq(startIndex, endIndex))).map(handleArrayResponse)
  }

  private val handleArrayResponse: (ErrorOr[RedisResponse]) => ErrorOr[ArrayResponse] =
    handleResponseWithErrorHandling {
      case replies: ArrayResponse => replies
    }

  private val handleOkStringResponse: ErrorOr[RedisResponse] => ErrorOr[OkStringResponse.type] =
    handleResponseWithErrorHandling {
      case OkStringResponse => OkStringResponse
    }

  private val handleIntegerResponse: ErrorOr[RedisResponse] => ErrorOr[IntegerResponse] =
    handleResponseWithErrorHandling {
      case r: IntegerResponse => r
    }

  private val handleBulkResponse: ErrorOr[RedisResponse] => ErrorOr[BulkResponse] =
    handleResponseWithErrorHandling {
      case r: BulkResponse => r
    }

}
