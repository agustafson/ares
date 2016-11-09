package ares

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ExecutorService, Executors}

import ares.RedisCommands._
import ares.interpreter.{Fs2CommandInterpreter, Fs2DatabaseInterpreter}
import cats.RecursiveTailRecM
import fs2.interop.cats.monadToCats
import fs2.{Strategy, Task}
import org.scalacheck.{Gen, Prop, Properties}
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class RedisTest extends Specification with ScalaCheck {
  implicit lazy val taskRecursiveTailRecM = new RecursiveTailRecM[Task] {}
  val databaseCounter = new AtomicInteger(0)

  trait RedisClientScope extends Scope {
    private val threadName: String = "redis.threadfactory"
    private val executor: ExecutorService = Executors.newFixedThreadPool(8, Strategy.daemonThreadFactory(threadName))
    implicit val strategy = Strategy.fromExecutor(executor)
    implicit val acg = AsynchronousChannelGroup.withThreadPool(executor)

    val databaseIndex = databaseCounter.getAndIncrement()

    private val redisHost: InetSocketAddress = new InetSocketAddress("127.0.0.1", 6379)
    val commandInterpreter = new Fs2CommandInterpreter[Task](redisHost)

    val databaseInterpreter: Fs2DatabaseInterpreter[Task] = new Fs2DatabaseInterpreter[Task](redisHost)
    databaseInterpreter.select(databaseIndex).unsafeRun()

    def runCommand[T](op: ops.CommandOp[T]): T = {
      commandInterpreter.run(op).unsafeRun()
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
