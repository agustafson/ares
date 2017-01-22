package redscaler

import fs2.Task
import fs2.interop.cats.monadToCats
import org.specs2.execute.{AsResult, Result}
import redscaler.RedisCommands.ops

trait RedisCommandsScope {
  implicit class CommandInterpreterW(val commandInterpreter: RedisCommands.Interp[Task]) {
    def runCommand[T](op: ops.CommandOp[T]): T = commandInterpreter.run(op).unsafeRun()
  }

  def withRepository[R: AsResult](f: RedisCommands.Interp[Task] => R): Result = {
    RedisDatabaseScope.dbPool.acquire() { db =>
      try {
        db.selectDatabase()
        val result = f(db.commandInterpreter)
        AsResult(result)
      } finally {
        db.flushDb()
      }
    }
  }
}
