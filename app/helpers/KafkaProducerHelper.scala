package helpers


import akka.Done
import akka.actor.ActorSystem
import akka.stream.Materializer
import cakesolutions.kafka.{KafkaProducer, KafkaProducerRecord}
import com.google.inject.Inject
import com.typesafe.config.Config
import helpers.implicits._
import models.internal._
import models.public._
import org.apache.kafka.common.serialization.StringSerializer
import play.api.Configuration
import services.serializers.JsonSerializer

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class KafkaProducerHelper @Inject()(config: Config,
                                    processorHelper: ProcessorHelper,
                                    configuration: Configuration)
                                   (implicit actorSystem: ActorSystem,
                                    executionContext: ExecutionContext,
                                    materializer: Materializer) {

  private val producer = KafkaProducer(
    KafkaProducer.Conf(
      config,
      keySerializer = new StringSerializer,
      valueSerializer = new JsonSerializer[Seq[SendData]]
    )
  )

  def sendToKafka[T](meterId: MeterId, sendData: Seq[SendData], processor: Processor)(implicit token: Token): Future[Done] = {
    producer.send(KafkaProducerRecord(processor.url, meterId.id.toString, sendData)).andThen {
      case Success(_) =>
      case Failure(exception) =>
        logger.error("Balancer.helpers.KafkaHelper: Exception during sending message to Kafka\n", exception)
        processorHelper.addToQueue(sendData.toList)
      case _ =>
        logger.error("Balancer.helpers.KafkaHelper: Unknown Exception during sending message to Kafka\n")
        processorHelper.addToQueue(sendData.toList)
    }.map { _ =>
      Done
    }
  }

  def close(): Unit = producer.close()

}
