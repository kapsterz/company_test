package controllers

import javax.inject._

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.Materializer
import models.public._
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import protocols.actors._
import akka.pattern._
import akka.util.Timeout
import models.internal.{Fail, SendData, Succ}
import helpers.implicits._
import play.api.Configuration
import play.api.libs.ws.WSClient

import scala.concurrent.duration._

@Singleton
class MainController @Inject()(@Named(PROCESSOR) processorActor: ActorRef)
                              (cc: ControllerComponents,
                               ws: WSClient)
                              (implicit val system: ActorSystem,
                               exc: ExecutionContext,
                               mat: Materializer,
                               configuration: Configuration) extends AbstractController(cc) {

  def ping = Action(Ok(Succ().toJson))

}
