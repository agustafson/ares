package redscaler

import fs2.Task
import fs2.interop.cats.monadToCats
import org.specs2.execute.{AsResult, Result}
import org.specs2.specification.{Context, Fixture}
import redscaler.RedisCommands.ops
import redscaler.RedisDatabaseScope.newRedisClient
import redscaler.interpreter.Fs2CommandInterpreter

trait RedisCommandsScope {
  implicit class CommandInterpreterW(val commandInterpreter: RedisCommands.Interp[Task]) {
    def runCommand[T](op: ops.CommandOp[T]): T = {
      commandInterpreter.run(op).unsafeRun() /*.fold[T]({ exception =>
        println(s"Error occurred running $op against ${commandInterpreter}")
      }, identity)*/
    }
  }

  def withRepository[R: AsResult](f: RedisCommands.Interp[Task] => R): Result = {
    RedisDatabaseScope.dbPool.acquire() { db =>
      try {
        db.selectDatabase()
        val result = f(db.commandInterpreter)
        println(s"Result from running operation against db ${db.dbIndex}: $result")
        AsResult(result)
      } catch {
        case ex: Throwable =>
          println(s"Error occurred running operation against db ${db.dbIndex}: $ex")
          throw ex
      } finally {
        db.flushDb()
      }
    }
  }
}
