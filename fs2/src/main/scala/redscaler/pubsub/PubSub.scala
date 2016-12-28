package redscaler.pubsub

import cats.free.Free
import freasymonad.cats.free
import fs2.Stream
import redscaler.ErrorOr

@free
trait PubSub {

  sealed trait PubSubOp[A]

  type PubSubIO[A] = Free[PubSubOp, A]

  def publish(channelName: String, message: Vector[Byte]): PubSubIO[ErrorOr[Int]]

  //def subscribe(channelName: String): PubSubIO[Stream[PubSubIO, SubscriberResponse]]

  def unsubscribe(channelName: String): PubSubIO[Unit]

}
