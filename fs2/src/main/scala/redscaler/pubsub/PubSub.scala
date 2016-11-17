package redscaler.pubsub

import cats.free.Free
import freasymonad.cats.free
import fs2.Stream

@free
trait PubSub {

  sealed trait PubSubOp[A]

  type PubSubIO[A] = Free[PubSubOp, A]

  def publish(channel: String, message: Vector[Byte]): PubSubIO[Int]

  def subscribe(channel: String): PubSubIO[Stream[Nothing, SubscriberMessage]]

  def unsubscribe(channel: String): PubSubIO[Unit]

}
