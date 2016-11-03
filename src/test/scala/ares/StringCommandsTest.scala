package ares

import ares.interpreter.JedisInterpreter
import org.scalacheck.{Gen, Prop, Properties}
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import redis.clients.jedis.Jedis


class StringCommandsTest extends Specification with ScalaCheck {
  sequential

  trait RedisClientScope extends Scope {
    val jInt = new JedisInterpreter(new Jedis())
  }

  val p1: Properties = new Properties("get/set string properties") with RedisClientScope {
    property("get unknown key returns None") = Prop.forAll(Gen.alphaStr)((key: String) => jInt.get(key) === None)

    property("get and set key returns correct value") = Prop.forAllNoShrink(Gen.alphaStr, Gen.alphaStr) { (key: String, value: String) =>
      val result = for {
        insertionResult <- jInt.set(key, value)
        resultValue <- jInt.get(key)
      } yield (insertionResult, resultValue)
      result === Some((true, value))
    }
  }

  s2"can get and set values$p1"

}
