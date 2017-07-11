package redscaler.interpreter

import fs2.util.Attempt
import fs2.{Chunk, Stream}
import org.scalacheck.{Gen, Prop, Properties}
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import redscaler._

import scala.collection.mutable.ListBuffer

class ResponseHandlerTest extends Specification with ResponseHandler[Attempt] with ScalaCheck {

  val p1: Properties = new Properties("handle redis response") {
    property("handle integer response") = Prop.forAll { (i: Long) =>
      getResponse(s""":$i\r\n""") === Vector(IntegerResponse(i))
    }

    property("handle string response") = Prop.forAll { (bytes: List[Byte]) =>
      val buffer = new ListBuffer[Byte]() +=
        RedisConstants.DOLLAR_BYTE ++= bytes.length.toString.getBytes ++= RedisConstants.CRLF ++=
        bytes ++= RedisConstants.CRLF

      getResponseFromBytes(buffer.result().toArray) === Vector(BulkResponse(Some(bytes.toVector)))
    }

    property("handle error response") = Prop.forAllNoShrink(Gen.alphaStr) { (errorMessage: String) =>
      getResponse(s"""-$errorMessage\r\n""") === Vector(ErrorResponse(errorMessage))
    }
  }

  private def getResponse(responseString: String): Vector[RedisResponse] = {
    getResponseFromBytes(responseString.getBytes)
  }

  private def getResponseFromBytes(bytes: Array[Byte]): Vector[RedisResponse] = {
    Stream
      .chunk(Chunk.bytes(bytes))
      .pull[Attempt, RedisResponse](handleResponse)
      .runLog
      .fold[Vector[RedisResponse]](ex => throw failure(ex.getMessage).exception, identity)
  }

  s2"can handle redis responses$p1"
}
