package redscaler

sealed trait RedisResponse

sealed trait ValidRedisResponse extends RedisResponse

case class SimpleStringReply(body: String) extends ValidRedisResponse

case class IntegerReply(long: Long) extends ValidRedisResponse

case class BulkReply(body: Option[Vector[Byte]]) extends ValidRedisResponse

case class ArrayReply(replies: List[RedisResponse]) extends ValidRedisResponse

case class ErrorReply(errorMessage: String) extends RedisResponse
