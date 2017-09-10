package helpers

import javax.inject.Named

import actors.BalancerActor
import akka.Done
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.QueueOfferResult.Enqueued
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Sink, SourceQueueWithComplete}
import com.google.inject.Inject
import helpers.implicits.logger
import models.internal._
import models.public._
import play.api.Configuration
import play.api.libs.ws.WSClient
import akka.stream.scaladsl.{Source => AkkaSrc}
import com.redis.{RedisClient, serialization}
import com.redis.serialization.Parse

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Random, Try}
import helpers.implicits._
import play.api.libs.json._
import protocols.actors.BALANCER

import scala.annotation.tailrec

class StreamQueueHelper @Inject()(@Named(BALANCER) balancerActor: ActorRef)
                                 (ws: WSClient,
                                  kafkaProducerHelper: KafkaProducerHelper,
                                  redisClient: RedisClient)
                                 (implicit configuration: Configuration,
                                  executionContext: ExecutionContext,
                                  materializer: Materializer,
                                  actorSystem: ActorSystem) {
  val redisKey: String = "chached_query"
  val redisTimeout: Int = 100
  val redisTryCount: Int = 1000

  val queueBufferSize: Int = configuration.get[Int]("common.queue.bufferSize")
  implicit val token: Token = configuration.get[String]("common.token")
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
        }
    }
    .to(Sink.ignore)
    .run()

  def createQuery: Data = Random.nextString(10)

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
        checkCache
      case None =>
        Thread.sleep(redisTimeout)
        checkCache
    }
  }
}
