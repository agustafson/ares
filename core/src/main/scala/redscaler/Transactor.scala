package redscaler

import redscaler.ConnectionOps.ops

trait Transactor[F[_]] {
  def execute[A](query: ops.ConnectionIO[A]): F[A]

  def transact[A](query: ops.ConnectionIO[A]): F[A]
}

object Transactor {
  def apply[F[_]](hostName: String, port: Int): Transactor[F] = new Transactor[F] {
    override def execute[A](query: ops.ConnectionIO[A]) = ???

    override def transact[A](query: ops.ConnectionIO[A]) = ???
  }
}
