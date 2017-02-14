package redscaler

trait Connection[F[_]] {
  def execute(command: Command): F[ErrorOr[RedisResponse]]
}
