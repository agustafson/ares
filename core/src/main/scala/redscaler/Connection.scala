package redscaler

trait Connection[F[_]] {
  def execute[T](command: Command): F[ErrorOr[RedisResponse]]
}
