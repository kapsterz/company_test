package controllers

import javax.inject._

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import helpers.{KafkaConsumerHelper, KafkaProducerHelper}
import helpers.implicits._
import models.internal.Succ
import play.api.Configuration
import play.api.libs.ws.WSClient
import play.api.mvc._
import protocols.actors._

import scala.concurrent.ExecutionContext

@Singleton
class MainController @Inject()(@Named(PROCESSOR) processorActor: ActorRef)
                              (cc: ControllerComponents,
                               kafkaProducerHelper: KafkaProducerHelper,
                               kafkaConsumerHelper: KafkaConsumerHelper,
                               ws: WSClient)
                              (implicit val system: ActorSystem,
                               exc: ExecutionContext,
                               mat: Materializer,
                               configuration: Configuration) extends AbstractController(cc) {

  def ping = Action(Ok(Succ().toJson))

}
