package redscaler

import cats.data.NonEmptyList
import cats.free.Free
import freasymonad.cats.free

@free
trait RedisCommands {

  sealed trait Command[A]

  type CommandOp[A] = Free[Command, A]

  def get(key: String): CommandOp[ErrorOr[Option[Vector[Byte]]]]

  def set(key: String, value: Vector[Byte]): CommandOp[ErrorOr[Unit]]

  def getset(key: String, value: Vector[Byte]): CommandOp[ErrorOr[Option[Vector[Byte]]]]

  def append(key: String, value: Vector[Byte]): CommandOp[ErrorOr[Int]]

  def lpush(key: String, values: NonEmptyList[Vector[Byte]]): CommandOp[ErrorOr[Int]]

  def lpushx(key: String, values: NonEmptyList[Vector[Byte]]): CommandOp[ErrorOr[Int]]

  def rpush(key: String, values: NonEmptyList[Vector[Byte]]): CommandOp[ErrorOr[Int]]

  def rpushx(key: String, values: NonEmptyList[Vector[Byte]]): CommandOp[ErrorOr[Int]]

  def lrange(key: String, startIndex: Int, endIndex: Int): CommandOp[ErrorOr[List[Vector[Byte]]]]

  def selectDatabase(databaseIndex: Int): CommandOp[ErrorOr[Unit]]

  def flushdb: CommandOp[ErrorOr[Unit]]

}
