package redscaler

import redscaler.ConnectionOps.ops.ConnectionIO

trait Connection[F[_]] {
  def execute(command: Command): F[ErrorOr[RedisResponse]]

  def execute[A](fa: ConnectionIO[A]): F[A]

  def execute[A, B](fa: ConnectionIO[A], fb: ConnectionIO[B]): F[(A, B)]
}
