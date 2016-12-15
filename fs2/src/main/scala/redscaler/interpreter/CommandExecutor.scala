package redscaler.interpreter

import com.typesafe.scalalogging.StrictLogging
import fs2.io.tcp.Socket
import fs2.util.syntax._
import fs2.util.{Applicative, Async, Catchable}
import fs2.{Chunk, Handle, Pipe, Pull, Stream, pipe}
import redscaler._
import redscaler.interpreter.ArgConverters.stringArgConverter
import redscaler.interpreter.RedisConstants._
import redscaler.pubsub._

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.duration._

abstract class CommandExecutor[F[_]: Applicative: Catchable](redisClient: Stream[F, Socket[F]]) extends StrictLogging {

  val writeTimeout = Some(2.seconds)
  val readTimeout  = Some(2.seconds)
  val maxBytesRead = 1024

  protected def runKeyCommand(command: String, key: String, args: Vector[Byte]*): F[RedisResponse] = {
    runCommand(command, stringArgConverter(key) +: args)
  }

  protected def runNoArgCommand(command: String): F[RedisResponse] = {
    runCommand(command, Seq.empty)
  }

  protected def runCommand[T](command: String, args: Seq[Vector[Byte]]): F[RedisResponse] = {
    sendCommand(createCommand(command, args))
  }

  protected def streamCommand(command: String, args: Seq[Vector[Byte]]): Stream[F, RedisResponse] = {
    streamCommandResults(createCommand(command, args)).map(RedisResponseHandler.handleResponse)
  }

  protected def createCommand(command: String, args: Seq[Vector[Byte]]): Chunk[Byte] = {
    val bytes = new mutable.ListBuffer() +=
        ASTERISK_BYTE ++= intCrlf(args.length + 1) +=
        DOLLAR_BYTE ++= intCrlf(command.length) ++=
        command.toArray.map(_.toByte) ++= CRLF ++=
        args.flatMap(arg => (DOLLAR_BYTE +: intCrlf(arg.length)) ++ arg ++ CRLF)

    logger.trace(s"command created: ${bytes.result().toVector.asString}")

    Chunk.bytes(bytes.result().toArray)
  }

  private def sendCommand(chunk: Chunk[Byte]): F[RedisResponse] = {
    val writeAndRead: (Socket[F]) => Stream[F, Vector[Byte]] = { socket =>
      Stream.chunk(chunk).to(socket.writes(writeTimeout)).drain.onFinalize(socket.endOfOutput) ++
        socket.reads(maxBytesRead, readTimeout).chunks.map(_.toVector)
    }
    redisClient.flatMap(writeAndRead).runFold(Vector.empty[Byte])(_ ++ _).map(RedisResponseHandler.handleResponse)
  }

  private def streamCommandResults(chunk: Chunk[Byte]): Stream[F, Byte] = {
    logger.trace(s"sending command $chunk")

    val writeAndRead: (Socket[F]) => Stream[F, Byte] = { socket =>
      Stream.chunk(chunk).to(socket.writes(writeTimeout)).drain.onFinalize(socket.endOfOutput) ++
        socket.reads(maxBytesRead, readTimeout)
    }
    redisClient.flatMap(writeAndRead)
  }

  val takeResponseH: Byte => Pull[F, SubscriberResponse, Handle[F, Byte]] = {
    case PLUS_BYTE =>
      processSimpleStringReply
    case DOLLAR_BYTE =>
      processBulkReply
    case ASTERISK_BYTE =>
      processMultiBulkReply
    case COLON_BYTE =>
      processInteger
    case MINUS_BYTE =>
      processError
  }

  def takeResponse(h: Handle[F, Byte]): Pull[F, ErrorReplyOr[SubscriberResponse], Byte] =
    for {
      (b, h) <- h.await1
      responsePipe = b match {
        case PLUS_BYTE =>
          processSimpleStringReply
        case DOLLAR_BYTE =>
          processBulkReply
        case ASTERISK_BYTE =>
          processMultiBulkReply
        case COLON_BYTE =>
          processInteger
        case MINUS_BYTE =>
          processError
        case b =>
          throw new RuntimeException("Unknown reply: " + b.toChar)
      }
      (result, h) <- responsePipe(h)
      //c = SubscriptionResponseHandler.handler(responsePipe)
      tl <- Pull.output1(result) as h
    } yield tl

  protected def subscribeAndPull(chunk: Chunk[Byte])(
      implicit async: Async[F]): Stream[F, ErrorReplyOr[SubscriberResponse]] = {
    val writeChunk: (Socket[F]) => Stream[F, Vector[Byte]] = { socket: Socket[F] =>
      Stream.chunk(chunk).to(socket.writes(writeTimeout)).drain.onFinalize(socket.endOfOutput)
    }
    val writeAndPull = { socket: Socket[F] =>
      Stream.chunk(chunk).to(socket.writes(writeTimeout)).drain.onFinalize(socket.endOfOutput) ++
        socket.reads(maxBytesRead, readTimeout)
    }

    val responseStream = redisClient.flatMap(writeAndPull).pull(takeResponse)
    /*.flatMap { bytes =>
      val (remainingBytes, redisResponse) = RedisResponseHandler.handleSingleResult.run(bytes)
      SubscriptionResponseHandler.handler(redisResponse)
    }*/
    responseStream
    /*
    for {
      q <- Stream.eval(fs2.async.unboundedQueue[F, ErrorReplyOr[SubscriberResponse]])
      _ <- redisClient.flatMap(writeChunk)
      _ <- redisClient.flatMap(
        _.reads(maxBytesRead, readTimeout).chunks
          .map(_.toVector)
          .map { bytes =>
            println(s"received async bytes: $bytes")
            SubscriptionResponseHandler.handler(RedisResponseHandler.handleResponse(bytes))
          }
          .observeAsync(q.enqueue, 10))
      row <- q.dequeue
    } yield row
     */
    /*
      .pull {
        _.await1Async.flatMap {
          _.pull.flatMap {
            case (subscriberResponse, handle) =>
              subscriberResponse match {
                case Right(_: Unsubscribe) =>
                  Pull.done
                case response: ErrorReplyOr[SubscriberResponse] =>
                  Pull.output1(response) as handle
              }
          }
        }
      }
   */
  }

  type BytePipe[A] = Pipe[F, Byte, A]
  object BytePipe {
    def apply[A](f: Stream[F, Byte] => Stream[F, A]): BytePipe[A] = f
  }

  private val takeLineH: Handle[F, Byte] => Pull[F, Vector[Byte], Handle[F, Byte]] = {
    def takeByte(bytes: Vector[Byte])(h: Handle[F, Byte]): Pull[F, Vector[Byte], Handle[F, Byte]] = {
      if (bytes endsWith CRLF) {
        Pull.output1[F, Vector[Byte]](bytes) as h
      } else
        h.receive1 {
          case (b, tl) =>
            takeByte(bytes :+ b)(tl)
        }
    }
    takeByte(Vector.empty)

    /*Pull.loop {
      (_: Handle[F, Byte]).awaitN(2, allowFewer = false).flatMap {
        case (chunks, h) =>
          val currentByteChunk = chunks.flatMap(_.toVector).toVector
          if (currentByteChunk == CRLF)
            Pull.done
          else
            Pull.output1(currentByteChunk) as h
      }
    }*/
  }

  private val takeLine: BytePipe[Vector[Byte]] =
    BytePipe {
      _.vectorChunkN(2, allowFewer = false).split(_ == CRLF).map(_.flatten)
    }

//  private def takeContentAndNewLine[I](using: Handle[F, Byte] => Pull[F, Vector[Byte], (I, Handle[F, Byte])]): BytePipe[Any] =
//    _.repeatPull { handle =>
//      for {
//        result <- using(handle).flatMap { case (chunks, h) => Pull.output1(chunks).as(h.drop(2)) }
//      } yield result
//      /*
//      for {
//        firstLine <- stream(handler)
//        _ <- stream.drop(2)
//      } yield firstLine
//      */
//    }

  private def takeContentByLengthAndNewLine(contentLength: Int): Pipe[F, Byte, Vector[Byte]] =
    _.pull {
      _.take(contentLength).flatMap(_.drop(2))
//      _.awaitN(contentLength, allowFewer = false).flatMap {
//        case (chunks, h) =>
//          val result = for {
//            h2 <- h.drop(2)
//            result <- Pull.output1(chunks.flatMap(_.toVector).toVector).as(h2)
//          } yield result
//          result
//      }
    }

  /*
    BytePipe { bytes =>
      for {
        firstLine <- bytes.chunkLimit(contentLength)
        _         <- bytes.drop(2)
      } yield firstLine.toVector
    }
   */

  private val takeIntegerLineH: Handle[F, Byte] => Pull[F, Int, Handle[F, Byte]] = { h =>
    takeLineH(h)
  }

  private val takeIntegerLine: BytePipe[Long] =
    takeLine.map(_.map(_.toChar).mkString.toLong)

  private val processSimpleStringReply: BytePipe[SimpleStringReply] =
    takeLine.map(bytes => SimpleStringReply(bytes.asString))

  private val processBulkReply: BytePipe[BulkReply] = BytePipe { stream =>
    stream.zipWithNext.takeWhile {
      case (`CR`, Some(`LF`)) => false
      case _                  => true
    } //.fold()
    //stream.mapAccumulate(Vector.empty[Byte]){ case (bytes, byte1) => (bytes :+ byte1, ) }

    /*
    stream.vectorChunkN(2, allowFewer = false).pull { handle =>
      // take integer line, but don't output result - only use to make decision.
      handle.takeWhile(_ != CRLF)
    }.pull { handle =>

    }
     */
    /*
      handle.flatMap {
        case (chunks, h) =>
          Pull.output1[F, Vector[Byte]](chunks.flatMap(_.toVector).toVector) as h
      }
     */

    ???
  /*
      handle.awaitN(2, allowFewer = false).map( {
        case (chunks, h) =>
          chunks.flatMap(_.toVector).toVector
      }).split(_ == CRLF).map(_.flatten)
      takeLine(handle).flatMap { bytes =>
        val messageLength = bytes.map(_.toChar).mkString.toLong

      }
   */
  // if message length is -1, return empty BulkReply

  // otherwise, take content of message length from remainder of stream

  /*
      _.await1.flatMap {
        case (messageLength, handle) =>
          if (messageLength == -1)
            Pull.output1(BulkReply(None)) as handle
          else

            takeContentByLengthAndNewLine(messageLength.toInt) as handle
      }
   */
  /*.through { inputStream =>
        inputStream.flatMap { messageLength =>
          if (messageLength == -1)
            Stream.emit(BulkReply(None))
          else {
            takeContentByLengthAndNewLine(messageLength.toInt)
          }
      }
    }
   */
  }

  private val processMultiBulkReply: BytePipe[ArrayReply] = {
    @tailrec
    def takeReplies(remainingCount: Int, currentPipe: BytePipe[List[RedisResponse]]): BytePipe[List[RedisResponse]] = {
      if (remainingCount > 0) {
        val newPipe = currentPipe.transform {
          case (currentBytes, responses) =>
            val (remainingBytes, reply) = handleSingleResult.run(currentBytes)
            (remainingBytes, responses :+ reply)
        }
        takeReplies(remainingCount - 1, newPipe)
      } else currentPipe
    }

    takeIntegerLine.flatMap { numberOfArgs =>
      takeReplies(numberOfArgs.toInt, BytePipe { bytes =>
        (bytes, List.empty[RedisResponse])
      }).map(replies => ArrayReply(replies))
    }
  }

  private def processInteger: BytePipe[IntegerReply] = takeIntegerLine.map(IntegerReply)

  private def processError: BytePipe[ErrorReply] = takeLine.map(bytes => ErrorReply(bytes.asString))

}

object CommandExecutor {
  def handleReplyWithErrorHandling[A](
      handler: PartialFunction[RedisResponse, A]): PartialFunction[RedisResponse, ErrorReplyOr[A]] = {
    handler.andThen(Right[ErrorReply, A]).orElse {
      case errorReply: ErrorReply => Left[ErrorReply, A](errorReply)
      case unknownReply           => throw new RuntimeException("boom")
    }
  }
}
