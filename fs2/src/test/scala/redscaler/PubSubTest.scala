package redscaler

import fs2.{Handle, Pull, Task}
import org.specs2.mutable.Specification
import redscaler.interpreter.ArgConverters._
import redscaler.interpreter.Fs2PubSubInterpreter
import redscaler.pubsub.{Message, Subscribe, SubscriberResponse}

import scala.collection.mutable.ArrayBuffer

class PubSubTest extends Specification {
  sequential

  trait PubSubScope extends RedisClientScope {
    val publisher: Fs2PubSubInterpreter[Task] =
      new Fs2PubSubInterpreter[Task](new Fs2Connection[Task](newRedisClient))
    val subscriber: Fs2PubSubInterpreter[Task] =
      new Fs2PubSubInterpreter[Task](new Fs2Connection[Task](newRedisClient))
  }

//  "publish with no subscribers" in new PubSubScope {
//    val channelName   = "heyhey"
//    val receiverCount = publisher.publish(channelName, "Hello").unsafeRun()
//    receiverCount ==== 0
//  }

  /*
  "subscribe to keyspace notifications" in new PubSubScope {
    val channelName      = "__keyevent@0__:set"
    val subscribedStream = subscriber.subscribe(channelName)

    val buffer = new ListBuffer[ErrorOr[SubscriberResponse]]()

    subscribedStream.repeatPull(_.take(1)).runLog.unsafeRunAsync {
      case Left(ex) =>
        println(s"Error occurred getting result: $ex")
      case Right(result) =>
        buffer ++= result
        println(s"result from unsafeasync run was: $result")
    }
//    println("creating queue")
//    val queue = fs2.async.unboundedQueue[Task, ErrorOr[SubscriberResponse]].unsafeRun()
//    subscribedStream.to(queue.enqueue).run.unsafeRunAsync { result =>
//      println(s"result from unsafeasync run was: $result")
//    }
//    println(s"queue created: $t")

    Thread.sleep(500)

    val message: Vector[Byte] = "bar"
    new Fs2CommandInterpreter[Task](newRedisClient).set("foo", message).unsafeRun() ==== Right(())

    eventually {
      buffer should haveSize(2)
    }

//    println(s"queue size: ${queue.size.get.unsafeRun()}")
//    println("getting first result")
//    queue.dequeue1.unsafeRun() ==== Right(Subscribe(channelName, 1))
//    println("getting second result")
//    queue.dequeue1.unsafeRun() ==== Right(Message(channelName, message))
  }
   */

  "subscribe to published message" in new PubSubScope {
    val channelName                    = "heyhey2"
    val messageContents1: Vector[Byte] = "Hello"
    val messageContents2: Vector[Byte] = "World"
    val subscribedStream               = subscriber.subscribe(channelName)

    //val subscriberPull = subscribedStream.open

    val reader = (handle: Handle[Task, ErrorOr[SubscriberResponse]]) =>
      handle.await1.flatMap {
        case (chunks, handle2) =>
          println(s"got chunks $chunks")
          Pull.output1(chunks) as handle2
    }

//    val receiverCount = (for {
//      _ <- subscribedStream.run
//      //subscribeResult    <- subscribedStream.take(1).runLog
//      receiverCount <- publisher.publish(channelName, messageContents1)
//    } yield (receiverCount)).unsafeRun()
    //val process = subscribedStream.pull(reader).runLog

    val resultsBuffer = new ArrayBuffer[ErrorOr[SubscriberResponse]]()
    println("creating queue")
    val queue = fs2.async.unboundedQueue[Task, ErrorOr[SubscriberResponse]].unsafeRun()
    subscribedStream.observeAsync(queue.enqueue, 5).run.unsafeRunAsyncFuture()
    println("queue created")

//    println("running subscribed stream...")
//    subscribedStream.runLog.unsafeRunAsyncFuture()
//    println("subscribed stream runing")

    Thread.sleep(500)

    println("publishing message ...")
    publisher.publish(channelName, messageContents1).unsafeRun() ==== Right(1)
    publisher.publish(channelName, messageContents2).unsafeRun() ==== Right(1)

//    val sink: fs2.Pipe[Task, ErrorOr[SubscriberResponse], scala.Unit] = { response =>
//      resultsBuffer += response
//    }
    //fs2.pipe.mapAccumulate()
    //val process2 = fs2.pipe.observeAsync(subscribedStream.pull(reader), 10)

    //receiverCount === 1

    //val subscribedMessages: Stream[Task, ErrorOr[SubscriberResponse]] = subscribedStream
//      subscribedStream.repeatPull[ErrorOr[SubscriberResponse]] {
//        Pull.loop(reader)
//      }
    //println(s"subscribed stream: $subscribedStream")
    //val subscribedMessages = subscriberPull.flatMap(_.take(2)).close.runLog.unsafeRun()
    //.take(2).runLog.unsafeRun() ==== Vector(Right(Subscribe(channelName, 1)))
    //subscribeResult ==== Vector(Right(Subscribe(channelName, 1)))
    println("dequeueing message 1")
    queue.dequeue1.unsafeRun() ==== Right(Subscribe(channelName, 1))

    println("dequeueing message 2")
    queue.dequeue1.unsafeRun() ==== Right(Message(channelName, messageContents1))
    queue.dequeue1.unsafeRun() ==== Right(Message(channelName, messageContents2))

//    queue.dequeueBatch1(1).unsafeRun().toVector ==== Vector(Right(Subscribe(channelName, 1)),
//                                                            Right(Message(channelName, messageContents1)))
    //subscribedStream.runLog.unsafeRun() ==== Vector(Right(Subscribe(channelName, 1)))
    //receiverCount ==== 1
  }

}
