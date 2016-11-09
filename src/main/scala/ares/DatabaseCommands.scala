package ares

import cats.free._
import freasymonad.cats.free

import scala.language.higherKinds

@free trait DatabaseCommands {
  sealed trait DatabaseCommand[T]

  type DatabaseOp[T] = Free[DatabaseCommand, T]

  def select(databaseIndex: Int): DatabaseOp[Either[ErrorReply, Unit]]
}
