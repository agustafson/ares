package redscaler

import fs2.Task
import fs2.interop.cats.monadToCats
import org.scalacheck.{Gen, Properties}
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

    def selectNewDatabase: ErrorReplyOr[Unit] = {
      val databaseIndex = databaseCounter.getAndIncrement()
      commandInterpreter.selectDatabase(databaseIndex).unsafeRun
    }

    def flushdb: ErrorReplyOr[Unit] = {
      commandInterpreter.flushdb.unsafeRun()
    }
  }

  val p1: Properties = new Properties("get and set") with RedisCommandsScope {
    "get unknown key returns None" >> prop { (key: String) =>
      runCommand(ops.get(key)) ==== Right(None)
    }.beforeAfter(selectNewDatabase, flushdb)

    "set and then get returns same value" >> prop { (key: String, value: Vector[Byte]) =>
      val command = for {
        setResult <- ops.set(key, value)
        getResult <- ops.get(key)
      } yield (setResult, getResult)
      val (setResult, getResult) = runCommand(command)
      setResult ==== Right(())
      getResult ==== Right(Some(value))
    }.beforeAfter(selectNewDatabase, flushdb)

  }

  s2"Get and set$p1"

  val p2: Properties = new Properties("List operations") with RedisCommandsScope {
    "lrange for an unknown key returns an empty list" >> prop { (key: String, startIndex: Int, endIndex: Int) =>
      runCommand(ops.lrange(key, startIndex, endIndex)) ==== Right(List.empty)
    }

    "rpush single item and lrange" >> prop { (key: String, value: String) =>
      val op =
        for {
          count <- ops.rpush(key, value)
          result <- ops.lrange(key, 0, -1)
        } yield (count, result)
      val (count, result) = runCommand(op)
      count ==== Right(1)
      result ==== Right(List(value))
    }
      .noShrink
      .setGens(Gen.alphaStr.suchThat(_.trim.nonEmpty), Gen.alphaStr)
      .beforeAfter(selectNewDatabase, flushdb)
  }

  s2"List operations$p2"
}
