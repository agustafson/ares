package ares.interpreter

import ares._
import ares.interpreter.RedisResponseHandler._
import org.scalacheck.{Gen, Prop, Properties}
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification

import scala.collection.mutable.ListBuffer

class RedisResponseHandlerTest extends Specification with ScalaCheck {
  val p1: Properties = new Properties("handle redis response") {
    property("handle integer response") = Prop.forAll { (i: Long) =>
      getResponse(s""":$i\r\n""") === IntegerReply(i)
    }

    property("handle string response") = Prop.forAll { (bytes: List[Byte]) =>
      val buffer = new ListBuffer[Byte]() +=
          RedisConstants.DOLLAR_BYTE ++= bytes.length.toString.getBytes ++= RedisConstants.CRLF ++=
          bytes ++= RedisConstants.CRLF

      handleResponse(buffer.result().toVector) === BulkReply(Some(bytes.toVector))
    }

    property("handle error response") = Prop.forAllNoShrink(Gen.alphaStr) { (errorMessage: String) =>
      getResponse(s"""-$errorMessage\r\n""") === ErrorReply(errorMessage)
    }
  }

  private def getResponse(responseString: String): RedisResponse = {
    handleResponse(responseString.getBytes.toVector)
  }

  s2"can handle redis responses$p1"
}
