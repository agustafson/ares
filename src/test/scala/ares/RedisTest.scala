package ares

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.{ExecutorService, Executors}

import ares.RedisCommands._
import ares.interpreter.Fs2Interpreter
import cats.RecursiveTailRecM
import fs2.interop.cats.monadToCats
import fs2.{Strategy, Task}
import org.scalacheck.{Gen, Prop, Properties}
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class RedisTest extends Specification with ScalaCheck {
  implicit lazy val taskRecursiveTailRecM = new RecursiveTailRecM[Task] {}

  trait RedisClientScope extends Scope {
    private val threadName: String = "redis.threadfactory"
    private val executor: ExecutorService = Executors.newFixedThreadPool(8, Strategy.daemonThreadFactory(threadName))
    implicit val strategy = Strategy.fromExecutor(executor)
    implicit val acg = AsynchronousChannelGroup.withThreadPool(executor)

    val jInt = new Fs2Interpreter[Task](new InetSocketAddress("127.0.0.1", 6379))

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
