package redscaler

import org.scalacheck.Properties
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import redscaler.RedisCommands.ops

class StringCommandsTest extends Specification with RedisClientScope with ScalaCheck {
  sequential

  val stringProperties: Properties = new Properties("get and set") with RedisCommandsScope {
    "get unknown key returns None" >> prop { (key: String) =>
      withRepository { interpreter =>
        eventually {
          interpreter.runCommand(ops.get(key)) ==== Right(None)
        }
      }
    }

    "set and then get returns same value" >> prop { (key: String, value: Vector[Byte]) =>
      withRepository { interpreter =>
        val command = for {
          setResult <- ops.set(key, value)
          getResult <- ops.get(key)
        } yield (setResult, getResult)
        val (setResult, getResult) = interpreter.runCommand(command)
        setResult ==== Right(())
        getResult ==== Right(Some(value))
      }
    }

    "getset" >> prop { (key: String, value1: Vector[Byte], value2: Vector[Byte]) =>
      withRepository { interpreter =>
        val command = for {
          _        <- ops.set(key, value1)
          original <- ops.getset(key, value2)
          result   <- ops.get(key)
        } yield (original, result)
        val (original, result) = interpreter.runCommand(command)
        original ==== Right(Some(value1))
        result ==== Right(Some(value2))
      }
    }

    "append" >> prop { (key: String, value1: Vector[Byte], value2: Vector[Byte]) =>
      withRepository { interpreter =>
        val command = for {
          length1 <- ops.append(key, value1)
          length2 <- ops.append(key, value2)
          result  <- ops.get(key)
        } yield (length1, length2, result)
        val (length1, length2, result) = interpreter.runCommand(command)
        length1 ==== Right(value1.size)
        length2 ==== Right(value1.size + value2.size)
        result ==== Right(Some(value1 ++ value2))
      }
    }

  }

  s2"Get and set$stringProperties"
}
