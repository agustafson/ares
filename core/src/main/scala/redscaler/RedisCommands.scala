package redscaler

import cats.free._
import freasymonad.cats.free

import scala.language.higherKinds

@free
trait RedisCommands {

  sealed trait Command[A]

  type CommandOp[A] = Free[Command, A]

  def get(key: String): CommandOp[Option[Vector[Byte]]]

  def set(key: String, value: Vector[Byte]): CommandOp[ErrorReplyOrUnit]

  def selectDatabase(databaseIndex: Int): CommandOp[ErrorReplyOrUnit]

  def flushdb: CommandOp[ErrorReplyOrUnit]

}
