package redscaler.pubsub

sealed trait MessageType

case object Message extends MessageType
case object PMessage extends MessageType
