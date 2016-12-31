package redscaler

sealed trait RedisResponse

sealed trait ValidRedisResponse extends RedisResponse

sealed trait Error

case class SimpleStringReply(body: String) extends ValidRedisResponse

case class IntegerReply(long: Long) extends ValidRedisResponse

case class BulkReply(body: Option[Vector[Byte]]) extends ValidRedisResponse

case class ArrayReply(replies: List[RedisResponse]) extends ValidRedisResponse

case class ErrorReply(errorMessage: String) extends RedisResponse with Error

case class UnexpectedResponse(response: RedisResponse) extends Error

case object EmptyResponse extends Error

case class UnexpectedResponseType(responseType: Char)
    extends Exception(s"Received unexpexted response type: $responseType")
