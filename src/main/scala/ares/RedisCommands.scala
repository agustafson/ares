package ares

import ares.interpreter.RedisResponseHandler.ErrorReply
import cats._
import cats.free._
import freasymonad.cats.free

import scala.language.higherKinds

@free trait RedisCommands {
  sealed trait Command[A]
  type CommandOp[A] = Free[Command, A]

  def get(key: String): CommandOp[Option[String]]
  def set(key: String, value: String): CommandOp[Either[ErrorReply, Unit]]
}
