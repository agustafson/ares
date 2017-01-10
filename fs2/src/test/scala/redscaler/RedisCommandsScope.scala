package redscaler

import fs2.Task
import fs2.interop.cats.monadToCats
import org.specs2.execute.{AsResult, Result}
import org.specs2.specification.Context
import redscaler.RedisCommands.ops
import redscaler.interpreter.Fs2CommandInterpreter

trait RedisCommandsScope {
  val commandInterpreter = new Fs2CommandInterpreter[Task](RedisDatabaseScope.redisClient)

  def runCommand[T](op: ops.CommandOp[T]): T = {
    commandInterpreter.run(op).unsafeRun()
  }

  val dbContext = new Context {
    override def apply[T](a: => T)(implicit evidence$1: AsResult[T]): Result = {
      RedisDatabaseScope.dbPool.acquire() { db =>
        try {
          AsResult(a)
        } finally {
          db.flushDb()
        }
      }
    }
  }
}
