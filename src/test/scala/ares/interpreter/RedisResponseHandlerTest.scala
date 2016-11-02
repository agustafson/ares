package ares.interpreter

import ares.interpreter.RedisResponseHandler._
import org.scalacheck.{Arbitrary, Gen, Prop, Properties}
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification

import scala.collection.mutable.ListBuffer

class RedisResponseHandlerTest extends Specification with ScalaCheck  {
  //System.setProperty("file.encoding", "UTF-16")

  def getResponse(responseString: String) = {
    handleResponse(responseString.getBytes.toVector)
  }

  val p1: Properties = new Properties("handle redis response") {
    property("handle integer response") = Prop.forAll { (i: Long) =>
      getResponse(s""":$i\r\n""") === IntegerReply(i)
    }
    property("handle string response") = Prop.forAllNoShrink { (bytes: List[Byte]) =>
      //val string = new String(bytes.toArray)
      /*
      println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<--------------")
      println(s"input message (${bytes.length} long): >>>")
      println(bytes)
      println("<<<")

      val message = new StringBuilder()
        .append('$')
        .append(bytes.length)
        .append("\r\n")
        .appendAll(bytes.map(_.toChar))
        .append("\r\n")
        .result()
      println("sending message:")
      println(message)
      println("<<<")

      val response = getResponse(message)

      println("----------------------------->>>>>>>>>>>>>")
      println()
      println()
      println()
      */



      val buffer = new ListBuffer[Byte]() +=
        RedisConstants.DOLLAR_BYTE += bytes.length.toByte ++= RedisConstants.CRLF ++=
        bytes ++= RedisConstants.CRLF

      println("buffer: >>>")
      println(buffer.result)
      println("<<<")

      val response = handleResponse(buffer.result().toVector)
      response === BulkReply(bytes.toVector)
    }
  }

  s2"can handle redis responses$p1"
}
