package redscaler

import cats.syntax.cartesian._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import redscaler.ConnectionOps.ops

class CartesionOpsTest extends Specification with RedisClientScope {
  "Cartesion ops" should {
    "combine together" in new RedisCommandsScope with Scope {
      withRepository { interpreter =>
        val (key, value) = ("foo", "bar".getBytes.toVector)
        val op           = ops.set(key, value) *> ops.get(key)
        interpreter.runCommand(op) ==== Right(Some(value))
      }
    }
  }
}
