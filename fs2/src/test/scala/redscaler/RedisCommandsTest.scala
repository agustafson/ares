package redscaler

import cats.data.NonEmptyList
import fs2.Task
import fs2.interop.cats.monadToCats
import org.scalacheck.{Arbitrary, Gen, Properties}
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import redscaler.RedisCommands._
import redscaler.interpreter.{ArgConverters, Fs2CommandInterpreter}

class RedisCommandsTest extends Specification with ScalaCheck {
  sequential

  val nonEmptyStringGen: Gen[String] = Gen.alphaStr.suchThat(_.trim.nonEmpty)
  val byteVectorGen: Gen[Vector[Byte]] = Arbitrary.arbContainer[Vector, Byte].arbitrary

  trait RedisCommandsScope extends RedisClientScope {
    val commandInterpreter = new Fs2CommandInterpreter[Task](newRedisClient)

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

    "rpush single item and lrange" >> prop { (key: String, value: Vector[Byte]) =>
      val op =
        for {
          count <- ops.rpush(key, NonEmptyList.of(value))
          result <- ops.lrange(key, 0, -1)
        } yield (count, result)
      val (count, result) = runCommand(op)
      count ==== Right(1)
      result ==== Right(List(value))
    }
      .noShrink
      .setGens(nonEmptyStringGen, byteVectorGen)
      .beforeAfter(selectNewDatabase, flushdb)

    "rpush multiple items and lrange" >> prop { (key: String, value1: Vector[Byte], value2: Vector[Byte]) =>
      val op =
        for {
          count1 <- ops.rpush(key, NonEmptyList.of(value1))
          count2 <- ops.rpush(key, NonEmptyList.of(value2))
          result <- ops.lrange(key, 0, -1)
        } yield (count1, count2, result)
      val (count1, count2, result) = runCommand(op)
      count1 ==== Right(1)
      count2 ==== Right(2)
      result ==== Right(List(value1, value2))
    }
      .noShrink
      .setGens(nonEmptyStringGen, byteVectorGen, byteVectorGen)
      .beforeAfter(selectNewDatabase, flushdb)

    "lpush multiple items and lrange" >> prop { (key: String, value1: Vector[Byte], value2: Vector[Byte]) =>
      val op =
        for {
          count1 <- ops.lpush(key, NonEmptyList.of(value1))
          count2 <- ops.lpush(key, NonEmptyList.of(value2))
          result <- ops.lrange(key, 0, -1)
        } yield (count1, count2, result)
      val (count1, count2, result) = runCommand(op)
      count1 ==== Right(1)
      count2 ==== Right(2)
      result ==== Right(List(value2, value1))
    }
      .noShrink
      .setGens(nonEmptyStringGen, byteVectorGen, byteVectorGen)
      .beforeAfter(selectNewDatabase, flushdb)

    "lpush and rpush multiple items and lrange" >> prop { (key: String, leftValues: List[Vector[Byte]], rightValues: List[Vector[Byte]]) =>
      val op =
        for {
          count1 <- ops.rpush(key, NonEmptyList.fromList(rightValues).get)
          count2 <- ops.lpush(key, NonEmptyList.fromList(leftValues).get)
          result <- ops.lrange(key, 0, -1)
        } yield (count1, count2, result)
      val (count1, count2, result) = runCommand(op)
      count1 ==== Right(rightValues.size)
      count2 ==== Right(leftValues.size + rightValues.size)
      result ==== Right(leftValues.reverse ++ rightValues)
    }
      .noShrink
      .setGens(nonEmptyStringGen, Gen.nonEmptyListOf(byteVectorGen), Gen.nonEmptyListOf(byteVectorGen))
      .beforeAfter(selectNewDatabase, flushdb)

    "lpushx single item and lrange" >> prop { (key: String, value: Vector[Byte]) =>
      val wibble: Vector[Byte] = ArgConverters.stringArgConverter("wibble")
      val op =
        for {
          count1 <- ops.lpushx(key, NonEmptyList.of(value))
          _ <- ops.lpush(key, NonEmptyList.of(wibble))
          count2 <- ops.lpushx(key, NonEmptyList.of(value))
          result <- ops.lrange(key, 0, -1)
        } yield (count1, count2, result)
      val (count1, count2, result) = runCommand(op)
      count1 ==== Right(0)
      count2 ==== Right(2)
      result ==== Right(List(value, wibble))
    }
      .noShrink
      .setGens(nonEmptyStringGen, byteVectorGen)
      .beforeAfter(selectNewDatabase, flushdb)

    "rpushx single item and lrange" >> prop { (key: String, value: Vector[Byte]) =>
      val wibble: Vector[Byte] = ArgConverters.stringArgConverter("wibble")
      val op =
        for {
          count1 <- ops.rpushx(key, NonEmptyList.of(value))
          _ <- ops.rpush(key, NonEmptyList.of(wibble))
          count2 <- ops.rpushx(key, NonEmptyList.of(value))
          result <- ops.lrange(key, 0, -1)
        } yield (count1, count2, result)
      val (count1, count2, result) = runCommand(op)
      count1 ==== Right(0)
      count2 ==== Right(2)
      result ==== Right(List(wibble, value))
    }
      .noShrink
      .setGens(nonEmptyStringGen, byteVectorGen)
      .beforeAfter(selectNewDatabase, flushdb)

  }

  s2"List operations$p2"
}
