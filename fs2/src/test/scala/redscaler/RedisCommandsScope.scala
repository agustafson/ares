package redscaler

import fs2.Task
import fs2.interop.cats.monadToCats
import org.specs2.execute.{AsResult, Result}
import redscaler.ConnectionOps.ops

trait RedisCommandsScope {
  implicit class CommandInterpreterW(val commandInterpreter: ConnectionOps.Interp[Task]) {
    def runCommand[T](op: ops.ConnectionIO[T]): T = commandInterpreter.run(op).unsafeRun()
  }

  def withRepository[R: AsResult](f: ConnectionOps.Interp[Task] => R): Result = {
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
