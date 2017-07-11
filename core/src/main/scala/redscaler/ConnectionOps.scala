package redscaler

import cats.data.NonEmptyList
import cats.free.Free
import freasymonad.cats.free

@free
trait ConnectionOps {

  sealed trait ConnectionOp[A]

  type ConnectionIO[A] = Free[ConnectionOp, A]

  def get(key: String): ConnectionIO[ErrorOr[BulkResponse]]

  def set(key: String, value: Vector[Byte]): ConnectionIO[ErrorOr[OkStringResponse.type]]

  def getset(key: String, value: Vector[Byte]): ConnectionIO[ErrorOr[BulkResponse]]

  def append(key: String, value: Vector[Byte]): ConnectionIO[ErrorOr[IntegerResponse]]

  def lpush(key: String, values: NonEmptyList[Vector[Byte]]): ConnectionIO[ErrorOr[IntegerResponse]]

  def lpushx(key: String, values: NonEmptyList[Vector[Byte]]): ConnectionIO[ErrorOr[IntegerResponse]]

  def rpush(key: String, values: NonEmptyList[Vector[Byte]]): ConnectionIO[ErrorOr[IntegerResponse]]

  def rpushx(key: String, values: NonEmptyList[Vector[Byte]]): ConnectionIO[ErrorOr[IntegerResponse]]

  def lrange(key: String, startIndex: Int, endIndex: Int): ConnectionIO[ErrorOr[ArrayResponse]]

  def selectDatabase(databaseIndex: Int): ConnectionIO[ErrorOr[OkStringResponse.type]]

  def flushdb: ConnectionIO[ErrorOr[OkStringResponse.type]]

}
