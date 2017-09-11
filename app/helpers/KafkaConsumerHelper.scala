package helpers

import java.io.File

import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import akka.util.ByteString
import com.google.inject.Inject
import com.typesafe.config.Config
import models.internal.SendData
import models.public._
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import play.api.Configuration
import services.serializers.JsonDeserializer

import scala.concurrent.ExecutionContext

class KafkaConsumerHelper @Inject()(config: Config,
                                    configuration: Configuration)
                                   (implicit actorSystem: ActorSystem,
                                    executionContext: ExecutionContext,
                                    materializer: Materializer) {

  val consumerSettings: ConsumerSettings[String, Seq[SendData]] = ConsumerSettings(actorSystem, new StringDeserializer, new JsonDeserializer[Seq[SendData]])
    .withBootstrapServers(configuration.get[String]("bootstrap.servers"))
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
  val dataCollectorConf: URL = configuration.get[String]("common.dataCollector.topic")
  private val fileName = configuration.get[String]("common.dataCollector.outFileName")
  private val outFile = new File(fileName)


  private val consumerSource = Consumer.plainSource[String, Seq[SendData]](consumerSettings, Subscriptions.topics(dataCollectorConf))
    .map { message =>
      message.value().toList
    }
    .mapConcat(identity)
    .map(sendData => ByteString(sendData.data))
    .runWith(FileIO.toPath(outFile.toPath))

}
