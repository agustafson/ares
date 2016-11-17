package redscaler.pubsub

case class SubscriberMessage(messageType: MessageType, publishingChannelName: String, message: Vector[Byte])
