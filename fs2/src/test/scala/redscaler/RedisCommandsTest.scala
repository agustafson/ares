package redscaler

import fs2.Task
import fs2.interop.cats.monadToCats
import org.scalacheck.Properties
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import redscaler.RedisCommands._
import redscaler.interpreter.Fs2CommandInterpreter

class RedisCommandsTest extends Specification with ScalaCheck {
  sequential

  trait RedisCommandsScope extends RedisClientScope {
    val commandInterpreter = new Fs2CommandInterpreter[Task](redisClient)

    def runCommand[T](op: ops.CommandOp[T]): T = {
      commandInterpreter.run(op).unsafeRun()
    }

    def selectNewDatabase: ErrorReplyOrUnit = {
      val databaseIndex = databaseCounter.getAndIncrement()
      commandInterpreter.selectDatabase(databaseIndex).unsafeRun
    }

    def flushdb: ErrorReplyOrUnit = {
      commandInterpreter.flushdb.unsafeRun()
    }
  }

  val p1: Properties = new Properties("send and receive content") with RedisCommandsScope {
    "get unknown key returns None" >> prop { (key: String) =>
      runCommand(ops.get(key)) === None
    }.beforeAfter(selectNewDatabase, flushdb)

    "set and then get returns same value" >> prop { (key: String, value: Vector[Byte]) =>
      val command = for {
        setResult <- ops.set(key, value)
        getResult <- ops.get(key)
      } yield (setResult, getResult)
      val (setResult, getResult) = runCommand(command)
      setResult === Right(())
      getResult === Some(value)
    }.beforeAfter(selectNewDatabase, flushdb)

  }

  s2"Can send and receive content$p1"
}
