package redscaler

import redscaler.ConnectionOps.ops.ConnectionIO

class Fs2Transactor[F[_]](conn: Fs2Connection[F]) extends Transactor[F] {
  override def execute[A](op: ConnectionIO[A]): F[A] = ???

  override def transact[A](op: ConnectionIO[A]): F[A] = ???
}
