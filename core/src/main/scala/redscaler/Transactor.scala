package redscaler

import redscaler.ConnectionOps.ops.ConnectionIO

trait Transactor[F[_]] {
  def execute[A](op: ConnectionIO[A]): F[A]

  def transact[A](op: ConnectionIO[A]): F[A]
}
