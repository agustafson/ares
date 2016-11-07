package ares

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup

import ares.RedisCommands._
import ares.interpreter.Fs2TaskInterpreter
import cats.{Monad, RecursiveTailRecM}
import cats.free._
import fs2.{Strategy, Task}
import fs2.interop.cats.monadToCats
import org.scalacheck.{Gen, Prop, Properties}
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class RedisTest extends Specification with ScalaCheck {
  implicit lazy val taskRecursiveTailRecM = new RecursiveTailRecM[Task] {}

  trait RedisClientScope extends Scope {
    private val threadName: String = "redis.threadfactory"
    val strategy = Strategy.fromFixedDaemonPool(8, threadName)
    val acg = AsynchronousChannelGroup.withFixedThreadPool(8, Strategy.daemonThreadFactory(threadName))

    lazy val jInt = new Fs2TaskInterpreter(new InetSocketAddress("127.0.0.1", 6379))(acg, strategy)

    def runCommand[T](op: ops.CommandOp[T]): T = {
      jInt.run(op).unsafeRun()
    }
  }

  val p1: Properties = new Properties("send and receive content") with RedisClientScope {
    property("get unknown key returns None") = Prop.forAllNoShrink(Gen.const("helloworld")) { (key: String) =>
      runCommand(ops.get(key)) === None
    }

    property("get unknown key returns None") = Prop.forAllNoShrink(Gen.const("foo"), Gen.const("bar")) { (key: String, value: String) =>
      val command = for {
        setResult <- ops.set(key, value)
        getResult <- ops.get(key)
      } yield (setResult, getResult)
      val (setResult, getResult) = runCommand(command)
      setResult === Right(())
      getResult === Some(value)
    }

  }

  s2"Can send and receive content$p1"
}
