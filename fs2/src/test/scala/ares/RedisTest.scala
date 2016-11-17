package ares

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ExecutorService, Executors}

import ares.RedisCommands._
import ares.interpreter.Fs2CommandInterpreter
import cats.RecursiveTailRecM
import fs2.interop.cats.monadToCats
import fs2.io.tcp
import fs2.io.tcp._
import fs2.{Strategy, Stream, Task}
import org.scalacheck.Properties
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class RedisTest extends Specification with ScalaCheck {
  sequential

  implicit lazy val taskRecursiveTailRecM = new RecursiveTailRecM[Task] {}
  val databaseCounter                     = new AtomicInteger(0)

  trait RedisClientScope extends Scope {
    val threadName: String        = "redis.threadfactory"
    val executor: ExecutorService = Executors.newFixedThreadPool(8, Strategy.daemonThreadFactory(threadName))
    implicit val strategy         = Strategy.fromExecutor(executor)
    implicit val acg              = AsynchronousChannelGroup.withThreadPool(executor)

    val redisHost: InetSocketAddress = new InetSocketAddress("127.0.0.1", 6379)
    val redisClient: Stream[Task, Socket[Task]] =
      tcp.client[Task](redisHost, reuseAddress = true, keepAlive = true, noDelay = true)

    val commandInterpreter = new Fs2CommandInterpreter[Task](redisClient)

    def runCommand[T](op: ops.CommandOp[T]): T = {
      commandInterpreter.run(op).unsafeRun()
    }

    def selectNewDatabase: ErrorReplyOrUnit = {
      val databaseIndex = databaseCounter.getAndIncrement()
      commandInterpreter.selectDatabase(databaseIndex).unsafeRun
    }

    def flushdb: ErrorReplyOrUnit = {
      commandInterpreter.flushdb.unsafeRun()
    }
  }

  val p1: Properties = new Properties("send and receive content") with RedisClientScope {
    "get unknown key returns None" >> prop { (key: String) =>
      runCommand(ops.get(key)) === None
    }.beforeAfter(selectNewDatabase, flushdb)

    "set and then get returns same value" >> prop { (key: String, value: Vector[Byte]) =>
      val command = for {
        setResult <- ops.set(key, value)
        getResult <- ops.get(key)
      } yield (setResult, getResult)
      val (setResult, getResult) = runCommand(command)
      setResult === Right(())
      getResult === Some(value)
    }.beforeAfter(selectNewDatabase, flushdb)

  }

  s2"Can send and receive content$p1"
}
