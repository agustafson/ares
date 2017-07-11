package redscaler.pubsub

sealed trait SubscriberResponse

object SubscriberResponse {
  def apply(messageType: String, publishingChannelName: String, subscriberCount: Int): SubscriberResponse = {
    messageType.toUpperCase() match {
      case "SUBSCRIBE"    => Subscribe(publishingChannelName, subscriberCount)
      case "PSUBSCRIBE"   => PSubscribe(publishingChannelName, subscriberCount)
      case "UNSUBSCRIBE"  => Unsubscribe(publishingChannelName, subscriberCount)
      case "PUNSUBSCRIBE" => PUnsubscribe(publishingChannelName, subscriberCount)
    }
  }

  def apply(messageType: String, publishingChannelName: String, messageBody: Vector[Byte]): SubscriberResponse = {
    messageType.toUpperCase() match {
      case "MESSAGE"  => Message(publishingChannelName, messageBody)
      case "PMESSAGE" => PMessage(publishingChannelName, messageBody)
    }
  }
}

case class Subscribe(publishingChannelName: String, numberOfChannelsSubscribedTo: Int)  extends SubscriberResponse
case class PSubscribe(publishingChannelName: String, numberOfChannelsSubscribedTo: Int) extends SubscriberResponse

case class Unsubscribe(publishingChannelName: String, numberOfChannelsSubscribedTo: Int)  extends SubscriberResponse
case class PUnsubscribe(publishingChannelName: String, numberOfChannelsSubscribedTo: Int) extends SubscriberResponse

case class Message(publishingChannelName: String, message: Vector[Byte])  extends SubscriberResponse
case class PMessage(publishingChannelName: String, message: Vector[Byte]) extends SubscriberResponse
