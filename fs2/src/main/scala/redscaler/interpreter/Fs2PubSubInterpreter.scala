package redscaler.interpreter

import java.nio.channels.AsynchronousChannelGroup

import fs2.Stream
import fs2.io.tcp.Socket
import fs2.util.syntax._
import fs2.util.{Async, Functor}
import redscaler._
import redscaler.interpreter.ArgConverters._
import redscaler.pubsub.{Message, PubSub, Subscribe, SubscriberResponse}

class Fs2PubSubInterpreter[F[_]: Functor](redisClient: Stream[F, Socket[F]])(implicit asyncM: Async[F],
                                                                             tcpACG: AsynchronousChannelGroup)
    extends CommandExecutor(redisClient)
    with PubSub.Interp[F] {

  override def publish(channelName: String, message: Vector[Byte]): F[ErrorOr[Int]] = {
    runKeyCommand("publish", channelName, message).map(CommandExecutor.handleResponseWithErrorHandling {
      case IntegerResponse(receiverCount) => receiverCount.toInt
    })
  }

  def subscribe(channelName: String): Stream[F, ErrorOr[SubscriberResponse]] = {
    subscribeAndPull(createCommand("subscribe", Seq(channelName)))
  }

  override def unsubscribe(channelName: String): F[Unit] = ???
}

object SubscriptionResponseHandler {
  private val subscribeMsg: Vector[Byte] = stringArgConverter("subscribe")
  private val messageMsg: Vector[Byte]   = stringArgConverter("message")

  val handler: Function[ErrorOr[RedisResponse], ErrorOr[SubscriberResponse]] =
    CommandExecutor.handleResponseWithErrorHandling {
      case ArrayResponse(
          BulkResponse(Some(`subscribeMsg`)) :: BulkResponse(Some(publishingChannelName)) :: IntegerResponse(
            subscribedCount) :: Nil) =>
        Subscribe(publishingChannelName.asString, subscribedCount.toInt)
      case ArrayResponse(
          BulkResponse(Some(`messageMsg`)) :: BulkResponse(Some(publishingChannelName)) :: BulkResponse(
            Some(messageContent)) :: Nil) =>
        Message(publishingChannelName.asString, messageContent)
    }
}
