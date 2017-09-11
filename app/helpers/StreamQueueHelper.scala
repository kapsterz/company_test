package helpers

import javax.inject.Named

import akka.Done
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.QueueOfferResult.Enqueued
import akka.stream.scaladsl.{Sink, SourceQueueWithComplete, Source => AkkaSrc}
import akka.stream.{Materializer, OverflowStrategy}
import com.google.inject.Inject
import com.redis.serialization.Parse
import com.redis.{RedisClient, serialization}
import helpers.implicits.{logger, _}
import models.internal._
import models.public._
import play.api.Configuration
import play.api.libs.json._
import play.api.libs.ws.WSClient
import protocols.actors.BALANCER

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class StreamQueueHelper @Inject()(@Named(BALANCER) balancerActor: ActorRef)
                                 (ws: WSClient,
                                  kafkaProducerHelper: KafkaProducerHelper)
                                 (implicit configuration: Configuration,
                                  executionContext: ExecutionContext,
                                  materializer: Materializer,
                                  actorSystem: ActorSystem) {
  //TODO: Move conf in to file
  val redisClient = new RedisClient("10.0.2.15", 6379)
  val redisKey: String = "chached_query"
  val redisTimeout: Int = 100
  val redisTryCount: Int = 1000
  Future(checkCache)
  val queueBufferSize: Int = configuration.get[Int]("common.queue.bufferSize")
  implicit val token: Token = configuration.get[String]("common.security.token")
  implicit val format: serialization.Format = com.redis.serialization.Format {
    case element: SendData =>
      element.toJson.toString().getBytes("UTF-8")
    case element =>
      element.toString.getBytes("UTF-8")
  }
  implicit val parse: Parse[SendData] = Parse(arrayByte =>
    Json.parse(arrayByte).as[SendData]
  )
  val queueBatchSize: Int = configuration.get[Int]("common.queue.batchSize")
  val queueParallelism: Int = configuration.get[Int]("common.queue.parallelism")
  val queueMaxProcessors: Int = configuration.get[Int]("common.queue.maxProcessors")
  val streamQueue: SourceQueueWithComplete[(SendData, Processor)] = AkkaSrc
    .queue[(SendData, Processor)](queueBufferSize, OverflowStrategy.backpressure)
    .mapAsync(queueParallelism) { case send@(sendData, _) =>
      Future(cacheQuery(sendData)).map { _ =>
        send
      }
    }
    .groupBy(queueMaxProcessors, _._2.url)
    .grouped(queueBatchSize)
    .map(seq => seq.map(_._1) -> seq.head._2)
    .mapAsync(queueParallelism) { case (sendData, processor) =>
      kafkaProducerHelper.sendToKafka(MeterId.generate, sendData, processor)
        .map(_ => Done)
        .recoverFatal {
          case ex if sendData.nonEmpty =>
            logger.error("Generator.actors.GeneratorActor: Error during sending data\n" + ex)
            balancerActor ! Add(token, processor)
            Done
        }.flatMap { _ =>
        sendData
          .akkaSrc
          .mapAsync(queueParallelism / 10) { sendDataSingle =>
            Future(deleteCacheQuery(sendDataSingle))
          }
          .runWith(Sink.ignore)
      }
    }
    .to(Sink.ignore)
    .run()

  @tailrec
  final def cacheQuery(sendData: SendData, _try: Long = 0): Done = {
    redisClient.lpush(redisKey, sendData) match {
      case Some(_) =>
        Done
      case None if _try < redisTryCount =>
        cacheQuery(sendData, _try + 1)
      case _ =>
        //TODO: Implement adding query to DB cache
        logger.warn("Balancer.helpers.StreamQueueHelper: Cant add query in to Redis")
        Done
    }
  }

  final def deleteCacheQuery(sendData: SendData, _try: Long = 0): Done = {
    redisClient.lrem(redisKey, 1, sendData) match {
      case Some(_) =>
        Done
      case None if _try < redisTryCount / 10 =>
        deleteCacheQuery(sendData, _try + 1)
      case _ =>
        logger.warn("Balancer.helpers.StreamQueueHelper: Cant delete query from Redis")
        Done
    }
  }

  def addQuery(sendData: SendData, processor: Processor): Future[Done] = streamQueue.offer((sendData, processor)).flatMap {
    case Enqueued =>
      Future.successful(Done)
    case _ =>
      balancerActor ! Add(sendData.token, processor)
      Future(cacheQuery(sendData))
  }

  @tailrec
  final def checkCache: Done = {
    Try(redisClient.lindex[SendData](redisKey, 0)).toOption.flatten match {
      case Some(sendData) =>
        balancerActor ! sendData
        deleteCacheQuery(sendData)
        checkCache
      case None =>
        Thread.sleep(redisTimeout)
        checkCache
    }
  }
}
