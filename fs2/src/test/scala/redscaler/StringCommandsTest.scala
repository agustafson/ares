package redscaler

import org.scalacheck.Properties
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import redscaler.ConnectionOps.ops

class StringCommandsTest extends Specification with RedisClientScope with ScalaCheck {
  sequential

  val stringProperties: Properties = new Properties("get and set") with RedisCommandsScope {
    "get unknown key returns None" >> prop { (key: String) =>
      withRepository { interpreter =>
        eventually {
          interpreter.runCommand(ops.get(key)) ==== Right(BulkResponse(None))
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
        setResult ==== Right(OkStringResponse)
        getResult ==== Right(BulkResponse(Some(value)))
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
        original ==== Right(BulkResponse(Some(value1)))
        result ==== Right(BulkResponse(Some(value2)))
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
        length1 ==== Right(IntegerResponse(value1.size.toLong))
        length2 ==== Right(IntegerResponse(value1.size.toLong + value2.size.toLong))
        result ==== Right(BulkResponse(Some(value1 ++ value2)))
      }
    }

  }

  s2"Get and set$stringProperties"
}
