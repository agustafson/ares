package redscaler

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.{ExecutorService, Executors}

import com.typesafe.scalalogging.StrictLogging
import fs2.io.tcp
import fs2.io.tcp.Socket
import fs2.util.{Applicative, Async, Catchable}
import fs2.{NonEmptyChunk, Pipe, Pull, Scheduler, Strategy, Stream, Task}
import redscaler.interpreter.ArgConverters._
import redscaler.interpreter.{CommandExecutor, _}
import redscaler.pubsub.SubscriberResponse

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

class RedisReader[F[_]: Applicative: Catchable](
    redisClient: Stream[F, Socket[F]])(implicit async: Async[F], strategy: Strategy, scheduler: Scheduler)
    extends CommandExecutor[F](redisClient) {
  def run: F[Vector[Either[UnexpectedResponse, SubscriberResponse]]] = {
    val writeCommand: (Socket[F]) => Stream[F, Socket[F]] = { socket: Socket[F] =>
      Stream.chunk(createCommand("SUBSCRIBE", Seq("ch1", "ch2"))).to(socket.writes(None)).drain ++
        Stream.emit(socket)
    //.onFinalize[F](socket.endOfOutput)
    }

    val writeAndPull: (Socket[F]) => Stream[F, NonEmptyChunk[Byte]] = { socket: Socket[F] =>
      Stream.chunk(createCommand("SUBSCRIBE", Seq("ch1", "ch2"))).to(socket.writes(None)).drain ++
        socket.reads(16, None).chunks.mapChunks { chunks =>
          logger.info(s"chunks: $chunks"); chunks
        }
    }

    def tracingPipe[A]: Pipe[F, A, A] = {
      _.map { item =>
        item match {
          case b: Byte =>
            logger.info(s"item: ${b.toChar}")
            item
          case item =>
            logger.info(s"item: ${item}")
            item
        }
      }
    }

    logger.info(s"Running...")

    //val queue = fs2.async.unboundedQueue[F, NonEmptyChunk[Byte]].unsafeRun()

    val reading: Stream[F, Either[UnexpectedResponse, SubscriberResponse]] = for {
      readResults <- redisClient.flatMap(writeCommand).repeatPull {
        _.receive1 {
          case (socket, handle) =>
            Pull.outputs(
              socket
                .reads(1024, timeout = None)
                .repeatPull(handleResponse)
                .map {
                  case ArrayResponse(
                      List(BulkResponse(Some(messageType)),
                           BulkResponse(Some(channelName)),
                           BulkResponse(Some(messageBody)))) =>
                    val subscriberResponse =
                      SubscriberResponse(messageType.asString, channelName.asString, messageBody)
                    logger.debug(s"got sub response: $subscriberResponse")
                    Right(subscriberResponse)
                  case ArrayResponse(
                      List(BulkResponse(Some(messageType)),
                           BulkResponse(Some(channelName)),
                           IntegerResponse(subscriberCount))) =>
                    val subscriberResponse =
                      SubscriberResponse(messageType.asString, channelName.asString, subscriberCount.toInt)
                    logger.debug(s"got sub response: $subscriberResponse")
                    Right(subscriberResponse)
                  case response =>
                    logger.debug(s"got unexpected response: $response")
                    Left(UnexpectedResponse(response))
                }
                .through(tracingPipe)) as handle
        }
      }
      /*
      readResults <- redisClient.repeatPull {
        _.receive1 {
          case (socket, handle) =>
            Pull.eval(socket.read(16)).flatMap { maybeChunk =>
              println(s"maybe chunk: $maybeChunk")
              maybeChunk.fold[Pull[F, Byte, Handle[F, Socket[F]]]](Pull.done) { chunk =>
                Pull.output(chunk) as handle
              }
            }
          //Pull.outputs(socket.reads(16, None)) as handle
        }
      }
     */
    } yield readResults

    def qPipe[A]: Pipe[F, A, A] = source => {
      val mkQueue = fs2.async.unboundedQueue[F, NonEmptyChunk[A]]
      Stream.eval(mkQueue).flatMap { queue =>
        val fill: Stream[F, Nothing] = source.chunks.to(queue.enqueue).drain
        val out: Stream[F, A]        = queue.dequeue.flatMap(Stream.chunk)
        fill merge out
      }
    }

    reading.through(qPipe).runLog

//    val futureResult =
//      redisClient.flatMap(writeAndPull).through(qPipe).observeAsync(queue.enqueue, 512).runLog.unsafeRunAsyncFuture()

//    val futureResult: Future[Vector[Byte]] = redisClient
//      .flatMap(writeAndPull)
//      .through(qPipe)
//      .runFold(Vector.empty[Byte])(_ ++ _.toVector)
//      .unsafeTimed(30.seconds)
//      .unsafeRunAsyncFuture()

    /*
    val futureResult: Future[Vector[Byte]] = redisClient
      .flatMap(writeAndPull)
      //.observeAsync(queue.enqueue, 1024)
//      .repeatPull(_.takeThrough { b =>
//        logger.info(s"stuff: ${b.toVector.asString}")
//        true
//      })
      .repeatPull { handle =>
        handle.receive1 {
          case (chunk, handle) =>
            logger.info(s"got chunk:\n${chunk.toVector.asString}")
            if (chunk.toVector endsWith stringArgConverter("quit")) {
              Pull.done
            } else {
              Pull.output(chunk) as handle
            }
        }
      }
//      .takeWhile { chunk =>
//        logger.info(s"got chunk:\n${chunk.toVector.asString}")
//        chunk.toVector endsWith stringArgConverter("quit")
//      }
      .runFold(Vector.empty[Byte])(_ :+ _)
      .unsafeTimed(30.seconds)
//      .unsafeRunAsync { result =>
//        logger.info(s"Result of running async: $result")
//      }
      .unsafeRunAsyncFuture()
     */

    //logger.info(s"queue size: ${queue.size.get.unsafeRun()}")

    //logger.info(s"queue results: ${queue.dequeue.runFold(Vector.empty[Byte])(_ ++ _.toVector).unsafeRun().asString}")

    //logger.info(s"result queue: ${queue.dequeueBatch1(4096).unsafeRun().toVector.flatMap(_.toVector).asString}")
    /*.unsafeRunAsync {
      case Left(ex) =>
        logger.error(s"Error occurred: ${ex.getMessage}")
      case Right(result) =>
        logger.info(s"result received: $result")
    }*/
  }
}

object RedisReaderApp extends App with StrictLogging {

  val threadName: String        = "redis.threadfactory"
  val executor: ExecutorService = Executors.newFixedThreadPool(8, Strategy.daemonThreadFactory(threadName))
  implicit val strategy         = Strategy.fromExecutor(executor)
  implicit val acg              = AsynchronousChannelGroup.withThreadPool(executor)
  implicit val scheduler        = Scheduler.fromFixedDaemonPool(2)

  val redisClient: Stream[Task, Socket[Task]] =
    tcp.client[Task](new InetSocketAddress("127.0.0.1", 6379), reuseAddress = true, keepAlive = true, noDelay = true)

  val futureResult = new RedisReader[Task](redisClient).run.unsafeRunAsyncFuture()

  Thread.sleep(30000)

  val result = Try(Await.result(futureResult, 30.seconds))
  logger.info(s"Final result: $result")

  logger.info("Finished")
}
