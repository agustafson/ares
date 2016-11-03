package ares

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup

import ares.interpreter.Fs2TaskInterpreter
import fs2.Strategy
import org.scalacheck.{Gen, Prop, Properties}
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class RedisTest extends Specification with ScalaCheck {
  trait RedisClientScope extends Scope {
    private val threadName: String = "redis.threadfactory"
    val strategy = Strategy.fromFixedDaemonPool(8, threadName)
    val acg = AsynchronousChannelGroup.withFixedThreadPool(8, Strategy.daemonThreadFactory(threadName))

    val jInt = new Fs2TaskInterpreter(new InetSocketAddress("127.0.0.1", 6379))(acg, strategy)
  }

  val p1: Properties = new Properties("send and receive content") with RedisClientScope {
    property("get unknown key returns None") = Prop.forAllNoShrink(Gen.const("helloworld"))((key: String) => jInt.get(key).unsafeRun() === None)
  }

  s2"Can send and receive content$p1"

  "redis" in new RedisClientScope {
    jInt.set("foo", "bar").unsafeRun() === Right(())
  }
}
