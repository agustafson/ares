package redscaler

import cats.data.NonEmptyList
import cats.syntax.foldable._
import org.scalacheck.{Arbitrary, Gen, Properties}
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import redscaler.RedisCommands._
import redscaler.interpreter.ArgConverters

class ListCommandsTest extends Specification with RedisClientScope with ScalaCheck {
  sequential

  val nonEmptyStringGen: Gen[String]   = Gen.alphaStr.suchThat(_.trim.nonEmpty)
  val byteVectorGen: Gen[Vector[Byte]] = Arbitrary.arbContainer[Vector, Byte].arbitrary
  val nonEmptyListOfBytes: Gen[NonEmptyList[Vector[Byte]]] =
    Gen.nonEmptyListOf(byteVectorGen).map(bytes => NonEmptyList.fromList(bytes).get)
  implicit val arbNonEmptyListOfBytes: Arbitrary[NonEmptyList[Vector[Byte]]] = Arbitrary(nonEmptyListOfBytes)

  val listProperties: Properties = new Properties("List operations") with RedisCommandsScope {
    "lrange for an unknown key returns an empty list" >> prop { (key: String, startIndex: Int, endIndex: Int) =>
      runCommand(ops.lrange(key, startIndex, endIndex)) ==== Right(List.empty)
    }

    "rpush single item and lrange" >> prop { (key: String, value: Vector[Byte]) =>
      val op =
        for {
          count  <- ops.rpush(key, NonEmptyList.of(value))
          result <- ops.lrange(key, 0, -1)
        } yield (count, result)
      val (count, result) = runCommand(op)
      count ==== Right(1)
      result ==== Right(List(value))
    }.noShrink.setGens(nonEmptyStringGen, byteVectorGen).setContext(dbContext)

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
    }.noShrink.setGens(nonEmptyStringGen, byteVectorGen, byteVectorGen).setContext(dbContext)

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
    }.noShrink.setGens(nonEmptyStringGen, byteVectorGen, byteVectorGen).setContext(dbContext)

    "lpush and rpush multiple items and lrange" >> prop {
      (key: String, leftValues: NonEmptyList[Vector[Byte]], rightValues: NonEmptyList[Vector[Byte]]) =>
        val op =
          for {
            count1 <- ops.rpush(key, rightValues)
            count2 <- ops.lpush(key, leftValues)
            result <- ops.lrange(key, 0, -1)
          } yield (count1, count2, result)
        val (count1, count2, result) = runCommand(op)
        count1 ==== Right(rightValues.size.toInt)
        count2 ==== Right(leftValues.size.toInt + rightValues.size.toInt)
        result ==== Right(leftValues.toList.reverse ++ rightValues.toList)
    }.noShrink
      .setGens(nonEmptyStringGen, nonEmptyListOfBytes, nonEmptyListOfBytes)
      .setContext(dbContext)

    "lpushx single item and lrange" >> prop { (key: String, value: Vector[Byte]) =>
      val wibble: Vector[Byte] = ArgConverters.stringArgConverter("wibble")
      val op =
        for {
          count1 <- ops.lpushx(key, NonEmptyList.of(value))
          _      <- ops.lpush(key, NonEmptyList.of(wibble))
          count2 <- ops.lpushx(key, NonEmptyList.of(value))
          result <- ops.lrange(key, 0, -1)
        } yield (count1, count2, result)
      val (count1, count2, result) = runCommand(op)
      count1 ==== Right(0)
      count2 ==== Right(2)
      result ==== Right(List(value, wibble))
    }.noShrink.setGens(nonEmptyStringGen, byteVectorGen).setContext(dbContext)

    "rpushx single item and lrange" >> prop { (key: String, value: Vector[Byte]) =>
      val wibble: Vector[Byte] = ArgConverters.stringArgConverter("wibble")
      val op =
        for {
          count1 <- ops.rpushx(key, NonEmptyList.of(value))
          _      <- ops.rpush(key, NonEmptyList.of(wibble))
          count2 <- ops.rpushx(key, NonEmptyList.of(value))
          result <- ops.lrange(key, 0, -1)
        } yield (count1, count2, result)
      val (count1, count2, result) = runCommand(op)
      count1 ==== Right(0)
      count2 ==== Right(2)
      result ==== Right(List(wibble, value))
    }.noShrink.setGens(nonEmptyStringGen, byteVectorGen).setContext(dbContext)

  }

  s2"List operations$listProperties"
}
