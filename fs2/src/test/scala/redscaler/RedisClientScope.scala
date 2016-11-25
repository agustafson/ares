package redscaler

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.{ExecutorService, Executors}
import java.util.concurrent.atomic.AtomicInteger

import cats.RecursiveTailRecM
import fs2.{Strategy, Stream, Task}
import fs2.io.tcp
import fs2.io.tcp.Socket
import org.specs2.specification.Scope

trait RedisClientScope extends Scope {
  implicit lazy val taskRecursiveTailRecM = new RecursiveTailRecM[Task] {}
  val databaseCounter                     = new AtomicInteger(0)

  val threadName: String        = "redis.threadfactory"
  val executor: ExecutorService = Executors.newFixedThreadPool(8, Strategy.daemonThreadFactory(threadName))
  implicit val strategy         = Strategy.fromExecutor(executor)
  implicit val acg              = AsynchronousChannelGroup.withThreadPool(executor)

  val redisHost: InetSocketAddress = new InetSocketAddress("127.0.0.1", 6379)
  def newRedisClient: Stream[Task, Socket[Task]] =
    tcp.client[Task](redisHost, reuseAddress = true, keepAlive = true, noDelay = true)
}
