package redscaler

import org.scalacheck.Properties
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import redscaler.RedisCommands.ops

class StringCommandsTest extends Specification with RedisClientScope with ScalaCheck {
  sequential

  val stringProperties: Properties = new Properties("get and set") with RedisCommandsScope {
    "get unknown key returns None" >> prop { (key: String) =>
      runCommand(ops.get(key)) ==== Right(None)
    }.setContext(dbContext)

    "set and then get returns same value" >> prop { (key: String, value: Vector[Byte]) =>
      val command = for {
        setResult <- ops.set(key, value)
        getResult <- ops.get(key)
      } yield (setResult, getResult)
      val (setResult, getResult) = runCommand(command)
      setResult ==== Right(())
      getResult ==== Right(Some(value))
    }.setContext(dbContext).noShrink

    "getset" >> prop { (key: String, value1: Vector[Byte], value2: Vector[Byte]) =>
      runCommand(for {
        _        <- ops.set(key, value1)
        original <- ops.getset(key, value2)
        result   <- ops.get(key)
      } yield {
        original ==== Right(Some(value1))
        result ==== Right(Some(value2))
      })
    }

    "append" >> prop { (key: String, value1: Vector[Byte], value2: Vector[Byte]) =>
      runCommand(for {
        length1 <- ops.append(key, value1)
        length2 <- ops.append(key, value2)
        result  <- ops.get(key)
      } yield {
        length1 ==== Right(value1.size)
        length2 ==== Right(value1.size + value2.size)
        result ==== Right(Some(value1 ++ value2))
      })
    }.setContext(dbContext).noShrink

  }

  s2"Get and set$stringProperties"
}
