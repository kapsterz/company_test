package services.serializers

import java.util

import org.apache.kafka.common.serialization.{Deserializer, StringDeserializer}
import play.api.libs.json.{Json, Reads}

class JsonDeserializer[A](implicit writes: Reads[A]) extends Deserializer[A] {

  private val stringDeserializer = new StringDeserializer

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit =
    stringDeserializer.configure(configs, isKey)

  override def deserialize(topic: String, data: Array[Byte]): A =
    Json.parse(stringDeserializer.deserialize(topic, data)).as[A]

  override def close(): Unit =
    stringDeserializer.close()

}
