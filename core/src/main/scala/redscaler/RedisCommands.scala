package redscaler

import cats.data.NonEmptyList
import cats.free._
import freasymonad.cats.free

import scala.language.higherKinds

@free
trait RedisCommands {

  sealed trait Command[A]

  type CommandOp[A] = Free[Command, A]

  def get(key: String): CommandOp[ErrorReplyOr[Option[Vector[Byte]]]]

  def set(key: String, value: Vector[Byte]): CommandOp[ErrorReplyOr[Unit]]

  def lpush(key: String, values: NonEmptyList[Vector[Byte]]): CommandOp[ErrorReplyOr[Int]]

  def rpush(key: String, values: NonEmptyList[Vector[Byte]]): CommandOp[ErrorReplyOr[Int]]

  def lrange(key: String, startIndex: Int, endIndex: Int): CommandOp[ErrorReplyOr[List[Vector[Byte]]]]

  def selectDatabase(databaseIndex: Int): CommandOp[ErrorReplyOr[Unit]]

  def flushdb: CommandOp[ErrorReplyOr[Unit]]

}
