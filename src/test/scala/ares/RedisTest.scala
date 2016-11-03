package ares

import java.net.InetSocketAddress

import ares.interpreter.Fs2TaskInterpreter
import org.scalacheck.{Gen, Prop, Properties}
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class RedisTest extends Specification with ScalaCheck {
  trait RedisClientScope extends Scope {
    val jInt = new Fs2TaskInterpreter(new InetSocketAddress("127.0.0.1", 6379))(SocketSpec.tcpACG, TestUtil.S)
  }

  val p1: Properties = new Properties("send and receive content") with RedisClientScope {
    property("get unknown key returns None") = Prop.forAllNoShrink(Gen.const("helloworld"))((key: String) => jInt.get(key).unsafeRun() === None)
  }

  s2"Can send and receive content$p1"

  "redis" in new RedisClientScope {
    jInt.set("foo", "bar").unsafeRun() === Right(())
  }
}
