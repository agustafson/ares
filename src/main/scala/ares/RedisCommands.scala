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
/*
@free trait KVStore {                     // you can use any names you like
type KVStoreF[A] = Free[GrammarADT, A]  // as long as you define a type alias for Free
sealed trait GrammarADT[A]              // and a sealed trait.

  // abstract methods are automatically lifted into part of the grammar ADT
  def put[T](key: String, value: T): KVStoreF[Unit]
  def get[T](key: String): KVStoreF[Option[T]]

  def update[T](key: String, f: T => T): KVStoreF[Unit] =
    for {
      vMaybe <- get[T](key)
      _      <- vMaybe.map(v => put[T](key, f(v))).getOrElse(Free.pure(()))
    } yield ()
}
*/
