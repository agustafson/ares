package redscaler

trait Connection[F[_]] {
  def runKeyCommand(command: String, key: String, args: Vector[Byte]*): F[ErrorOr[RedisResponse]]

  def runNoArgCommand(command: String): F[ErrorOr[RedisResponse]]

  def runCommand[T](command: String, args: Seq[Vector[Byte]]): F[ErrorOr[RedisResponse]]
}
