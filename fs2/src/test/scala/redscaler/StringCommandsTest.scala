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

  }

  s2"Get and set$stringProperties"
}
