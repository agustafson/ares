package redscaler

import fs2.{Chunk, Handle, NonEmptyChunk, Pull, Stream, Task}
import org.specs2.mutable.Specification
import redscaler.interpreter.Fs2PubSubInterpreter
import redscaler.interpreter.ArgConverters._
import redscaler.pubsub.{Message, Subscribe, SubscriberResponse}

import scala.collection.mutable.ArrayBuffer

class PubSubTest extends Specification {
  trait PubSubScope extends RedisClientScope {
    val publisher: Fs2PubSubInterpreter[Task]  = new Fs2PubSubInterpreter[Task](newRedisClient)
    val subscriber: Fs2PubSubInterpreter[Task] = new Fs2PubSubInterpreter[Task](newRedisClient)
  }

//  "publish with no subscribers" in new PubSubScope {
//    val channelName   = "heyhey"
//    val receiverCount = publisher.publish(channelName, "Hello").unsafeRun()
//    receiverCount ==== 0
//  }

  "subscribe to published message" in new PubSubScope {
    val channelName                   = "heyhey2"
    val messageContents: Vector[Byte] = "Hello"
    val subscribedStream              = subscriber.subscribe(channelName)

    //val subscriberPull = subscribedStream.open

    val reader = (handle: Handle[Task, ErrorReplyOr[SubscriberResponse]]) =>
      handle.await1.flatMap {
        case (chunks, handle2) =>
          println(s"got chunks $chunks")
          Pull.output1(chunks) as handle2
    }

    /*
    val receiverCount = (for {
      _ <- subscribedStream.run
      //subscribeResult    <- subscribedStream.take(1).runLog
      receiverCount <- publisher.publish(channelName, messageContents)
    } yield (receiverCount)).unsafeRun()
     */
    //val process = subscribedStream.pull(reader).runLog

    val resultsBuffer = new ArrayBuffer[ErrorReplyOr[SubscriberResponse]]()
    println("creating queue")
    val queue = fs2.async.unboundedQueue[Task, ErrorReplyOr[SubscriberResponse]].unsafeRun()
    subscribedStream.observeAsync(queue.enqueue, 2).run.unsafeRunAsyncFuture()
    println("queue created")

    println("running subscribed stream...")
    subscribedStream.runLog.unsafeRunAsyncFuture()
    println("subscribed stream runing")

    println("publishing message ...")
    val receiverCount = publisher.publish(channelName, messageContents).unsafeRun()
    println(s"message published; receiver count: $receiverCount")

    /*
    val sink: fs2.Pipe[Task, ErrorReplyOr[SubscriberResponse], scala.Unit] = { response =>
      resultsBuffer += response
    }
     */
    //fs2.pipe.mapAccumulate()
    //val process2 = fs2.pipe.observeAsync(subscribedStream.pull(reader), 10)

    //receiverCount === 1

    //val subscribedMessages: Stream[Task, ErrorReplyOr[SubscriberResponse]] = subscribedStream
    /*
      subscribedStream.repeatPull[ErrorReplyOr[SubscriberResponse]] {
        Pull.loop(reader)
      }
     */
    //println(s"subscribed stream: $subscribedStream")
    //val subscribedMessages = subscriberPull.flatMap(_.take(2)).close.runLog.unsafeRun()
    //.take(2).runLog.unsafeRun() ==== Vector(Right(Subscribe(channelName, 1)))
    //subscribeResult ==== Vector(Right(Subscribe(channelName, 1)))
    println("dequeueing message 1")
    queue.dequeue1.unsafeRun() ==== Right(Subscribe(channelName, 1))

    println("dequeueing message 2")
    queue.dequeue1.unsafeRun() ==== Right(Message(channelName, messageContents))

//    queue.dequeueBatch1(1).unsafeRun().toVector ==== Vector(Right(Subscribe(channelName, 1)),
//                                                            Right(Message(channelName, messageContents)))
    //subscribedStream.runLog.unsafeRun() ==== Vector(Right(Subscribe(channelName, 1)))
    //receiverCount ==== 1
  }

}
