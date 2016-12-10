package redscaler.pubsub

trait SubscriberResponseHandle {
  def withResponse(callback: Either[Throwable, SubscriberResponse] => Unit): Unit
}
