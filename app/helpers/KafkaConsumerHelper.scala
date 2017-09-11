package helpers

import javax.inject.Named

import akka.actor.{ActorRef, ActorSystem}
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.google.inject.Inject
import com.typesafe.config.Config
import helpers.implicits.topic
import models.internal.SendData
import models.public.{Add, Delete, Processor, Token}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import play.api.Configuration
import protocols.actors.PROCESSOR
import services.serializers.JsonDeserializer

import scala.concurrent.ExecutionContext

class KafkaConsumerHelper @Inject()(@Named(PROCESSOR) processorActor: ActorRef)
                                   (config: Config,
                                    processorHelper: ProcessorHelper,
                                    configuration: Configuration)
                                   (implicit actorSystem: ActorSystem,
                                    executionContext: ExecutionContext,
                                    materializer: Materializer) {

  val consumerSettings: ConsumerSettings[String, Seq[SendData]] = ConsumerSettings(actorSystem, new StringDeserializer, new JsonDeserializer[Seq[SendData]])
    .withBootstrapServers(configuration.get[String]("bootstrap.servers"))
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
  val token: Token = configuration.get[String]("common.security.token")

  processorActor ! Add(token, Processor(topic))

  private val consumerSource = Consumer.plainSource[String, Seq[SendData]](consumerSettings, Subscriptions.topics(topic))
    .map { message =>
      processorActor ! Delete(token, Processor(topic))
      message.value().toList
    }
    .via(processorHelper.executionFlow)
    .map(_ => processorActor ! Add(token, Processor(topic)))
    .runWith(Sink.ignore)

}
