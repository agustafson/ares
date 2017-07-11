package redscaler

sealed trait RedisResponse

sealed trait ValidRedisResponse extends RedisResponse

sealed trait Error

sealed class SimpleStringResponse(body: String) extends ValidRedisResponse

case object OkStringResponse extends SimpleStringResponse("OK")

case class IntegerResponse(long: Long) extends ValidRedisResponse

case class BulkResponse(body: Option[Vector[Byte]]) extends ValidRedisResponse

case class ArrayResponse(replies: List[RedisResponse]) extends ValidRedisResponse

case class ErrorResponse(errorMessage: String) extends RedisResponse with Error

case class UnexpectedResponse(response: RedisResponse) extends Error

case object EmptyResponse extends Error

case class UnexpectedResponseType(responseType: Char)
    extends Exception(s"Received unexpexted response type: $responseType")
