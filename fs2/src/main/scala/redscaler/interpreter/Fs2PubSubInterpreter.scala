package redscaler.interpreter

import java.nio.channels.AsynchronousChannelGroup

import fs2.Stream
import fs2.io.tcp.Socket
import fs2.util.syntax._
import fs2.util.{Async, Functor}
import redscaler.IntegerReply
import redscaler.interpreter.ArgConverters._
import redscaler.pubsub.{PubSub, SubscriberMessage}

class Fs2PubSubInterpreter[F[_]: Functor](redisClient: Stream[F, Socket[F]])(implicit asyncM: Async[F],
                                                                             tcpACG: AsynchronousChannelGroup)
    extends CommandExecutor(redisClient)
    with PubSub.Interp[F] {

  override def publish(channelName: String, message: Vector[Byte]): F[Int] = {
    runCommand("publish", channelName, message).map {
      case IntegerReply(receiverCount) => receiverCount.toInt
      case reply                       => throw new RuntimeException("boom")
    }
  }

  override def subscribe(channel: String): F[Stream[Nothing, SubscriberMessage]] = ???

  override def unsubscribe(channel: String): F[Unit] = ???
}
