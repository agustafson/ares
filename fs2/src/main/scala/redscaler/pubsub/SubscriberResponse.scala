package redscaler.pubsub

sealed trait SubscriberResponse

case class Subscribe(publishingChannelName: String, numberOfChannelsSubscribedTo: Int)  extends SubscriberResponse
case class PSubscribe(publishingChannelName: String, numberOfChannelsSubscribedTo: Int) extends SubscriberResponse

case class Unsubscribe(publishingChannelName: String, numberOfChannelsSubscribedTo: Int)  extends SubscriberResponse
case class PUnsubscribe(publishingChannelName: String, numberOfChannelsSubscribedTo: Int) extends SubscriberResponse

case class Message(publishingChannelName: String, message: Vector[Byte]) extends SubscriberResponse
case class PMessage(publishingChannelName: String, message: Vector[Byte]) extends SubscriberResponse
