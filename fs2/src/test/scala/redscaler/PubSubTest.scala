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

  "pub/sub" in new PubSubScope {
    val channelName   = "heyhey"
    val receiverCount = publisher.publish(channelName, "Hello").unsafeRun()
    receiverCount === 0
  }

}
