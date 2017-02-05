package redscaler

import cats.data.NonEmptyList
import cats.free.Free
import freasymonad.cats.free

@free
trait ConnectionOps {

  sealed trait ConnectionOp[A]

  type ConnectionIO[A] = Free[ConnectionOp, A]

  def get(key: String): ConnectionIO[ErrorOr[Option[Vector[Byte]]]]

  def set(key: String, value: Vector[Byte]): ConnectionIO[ErrorOr[Unit]]

  def getset(key: String, value: Vector[Byte]): ConnectionIO[ErrorOr[Option[Vector[Byte]]]]

  def append(key: String, value: Vector[Byte]): ConnectionIO[ErrorOr[Int]]

  def lpush(key: String, values: NonEmptyList[Vector[Byte]]): ConnectionIO[ErrorOr[Int]]

  def lpushx(key: String, values: NonEmptyList[Vector[Byte]]): ConnectionIO[ErrorOr[Int]]

  def rpush(key: String, values: NonEmptyList[Vector[Byte]]): ConnectionIO[ErrorOr[Int]]

  def rpushx(key: String, values: NonEmptyList[Vector[Byte]]): ConnectionIO[ErrorOr[Int]]

  def lrange(key: String, startIndex: Int, endIndex: Int): ConnectionIO[ErrorOr[List[Vector[Byte]]]]

  def selectDatabase(databaseIndex: Int): ConnectionIO[ErrorOr[Unit]]

  def flushdb: ConnectionIO[ErrorOr[Unit]]

}
