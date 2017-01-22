package redscaler

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup

import fs2.Stream
import fs2.io.tcp
import fs2.io.tcp.Socket
import fs2.util.Async

class ConnectionFactory[F[_]](redisHost: InetSocketAddress)(implicit ACG: AsynchronousChannelGroup, A: Async[F]) {
  def newRedisClient: Stream[F, Socket[F]] =
    tcp.client[F](redisHost, reuseAddress = true, keepAlive = true, noDelay = true)
}
