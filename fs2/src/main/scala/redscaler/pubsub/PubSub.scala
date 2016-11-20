package redscaler.pubsub

import cats.free.Free
import freasymonad.cats.free
import fs2.Stream

@free
trait PubSub {

  sealed trait PubSubOp[A]

  type PubSubIO[A] = Free[PubSubOp, A]

  def publish(channelName: String, message: Vector[Byte]): PubSubIO[Int]

  def subscribe(channelName: String): PubSubIO[Stream[Nothing, SubscriberMessage]]

  def unsubscribe(channelName: String): PubSubIO[Unit]

}
