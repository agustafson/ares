package redscaler

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.{ExecutorService, Executors}

import fs2.io.tcp
import fs2.io.tcp.Socket
import fs2.{Strategy, Stream, Task}
import org.specs2.specification.Scope

trait RedisClientScope extends Scope {
  val threadName: String        = "redis.threadfactory"
  val executor: ExecutorService = Executors.newFixedThreadPool(8, Strategy.daemonThreadFactory(threadName))
  implicit val strategy         = Strategy.fromExecutor(executor)
  implicit val acg              = AsynchronousChannelGroup.withThreadPool(executor)

  val redisHost: InetSocketAddress               = new InetSocketAddress("127.0.0.1", 6379)
  def newRedisClient: Stream[Task, Socket[Task]] = new ConnectionFactory[Task](redisHost).newRedisClient
}
