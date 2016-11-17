package redscaler

sealed trait RedisResponse

case class SimpleStringReply(body: String) extends RedisResponse

case class IntegerReply(long: Long) extends RedisResponse

case class BulkReply(body: Option[Vector[Byte]]) extends RedisResponse

case class ErrorReply(errorMessage: String) extends RedisResponse
