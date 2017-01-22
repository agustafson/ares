package redscaler

import java.util.concurrent.atomic.AtomicInteger

import com.typesafe.scalalogging.StrictLogging
import fs2.Task
import io.github.andrebeat.pool._
import redscaler.interpreter.Fs2CommandInterpreter

object RedisDatabaseScope extends RedisClientScope with StrictLogging {
  private val dbCounter = new AtomicInteger(1)

  val dbPool: Pool[RedisDatabase] = {
    Pool(
      1,
      () => {
        val commandInterpreter: RedisCommands.Interp[Task] = new Fs2CommandInterpreter[Task](newRedisClient)
        RedisDatabase(commandInterpreter, dbCounter.getAndIncrement())
      }
    )
  }
  scala.util.Try(dbPool.fill())
  logger.info(s"current pool size: ${dbPool.size()}, capacity: ${dbPool.capacity()}")
}

case class RedisDatabase(commandInterpreter: RedisCommands.Interp[Task], dbIndex: Int) {
  def selectDatabase(): Unit = handleError("select", commandInterpreter.selectDatabase(dbIndex))

  def flushDb(): Unit = handleError("flush", commandInterpreter.flushdb)

  private def handleError(operationName: String, task: Task[ErrorOr[Unit]]): Unit = {
    task.unsafeRun.fold[Unit]((error: Error) => {
      throw new RuntimeException(s"Could not $operationName database $dbIndex: $error")
    }, identity)
  }
}
