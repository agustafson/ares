package redscaler

import fs2.Task
import org.specs2.mutable.Specification
import redscaler.interpreter.Fs2PubSubInterpreter
import redscaler.interpreter.ArgConverters._

class PubSubTest extends Specification {
  trait PubSubScope extends RedisClientScope {
    val publisher: Fs2PubSubInterpreter[Task]  = new Fs2PubSubInterpreter[Task](redisClient)
    val subscriber: Fs2PubSubInterpreter[Task] = new Fs2PubSubInterpreter[Task](redisClient)
  }

  "publish with no subscribers" in new PubSubScope {
    val channelName   = "heyhey"
    val receiverCount = publisher.publish(channelName, "Hello").unsafeRun()
    receiverCount === 0
  }

  "subscribe to published message" in new PubSubScope {
    val channelName      = "heyhey2"
    val subscribedStream = subscriber.subscribe(channelName).unsafeRun()
    val receiverCount    = publisher.publish(channelName, "Hello").unsafeRun()
    receiverCount === 1
  }

}
