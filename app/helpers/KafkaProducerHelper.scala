package helpers


import javax.inject.Named

import akka.Done
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.Materializer
import com.typesafe.config.Config
import cakesolutions.kafka.{KafkaProducer, KafkaProducerRecord}
import com.google.inject.Inject
import models.internal._
import models.public._
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.requests.OffsetCommitResponse
import org.apache.kafka.common.serialization.StringSerializer
import play.api.Configuration
import protocols.actors.BALANCER
import services.serializers.JsonSerializer
import helpers.implicits._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class KafkaProducerHelper @Inject()(@Named(BALANCER) balancerActor: ActorRef)
                                   (config: Config,
                                    configuration: Configuration)
                                   (implicit actorSystem: ActorSystem,
                                    executionContext: ExecutionContext,
                                    materializer: Materializer) {

  private val producer = KafkaProducer(
    KafkaProducer.Conf(
      config,
      keySerializer = new StringSerializer,
      valueSerializer = new JsonSerializer[SendData]
    )
  )

  def sendToKafka[T](meterId: MeterId, sendData: SendData, processor: Processor)(implicit token: Token): Future[Done] = {
    producer.send(KafkaProducerRecord(processor.url, meterId.id.toString, sendData)).andThen {
      case Success(_) =>
        balancerActor ! Add(token, processor)
      case Failure(exception) =>
        logger.error("Balancer.helpers.KafkaHelper: Exception during sending message to Kafka\n", exception)
        balancerActor ! Add(token, processor)
        balancerActor ! sendData
      case _  =>
        logger.error("Balancer.helpers.KafkaHelper: Unknown Exception during sending message to Kafka\n")
        balancerActor ! sendData
    }.map { _ =>
      Done
    }
  }

  def close(): Unit = producer.close()

}
