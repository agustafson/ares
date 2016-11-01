package ares

import ares.interpreter.JedisInterpreter
import org.scalacheck.{Prop, Properties}
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import redis.clients.jedis.Jedis


class StringCommandsTest extends Specification with ScalaCheck {
  trait RedisClientScope extends Scope {
    val jInt = new JedisInterpreter(new Jedis())
  }

  val p1: Properties = new Properties("get/set string properties") with RedisClientScope {
    property("get unknown key returns None") = Prop.forAll((key: String) => jInt.get(key) === None)
  }

  s2"can get and set values$p1"

}
