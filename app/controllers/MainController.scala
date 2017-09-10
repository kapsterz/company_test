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
import actors.BalancerActor
import play.api.libs.ws.WSClient

import scala.concurrent.duration._

@Singleton
class MainController @Inject()(@Named(BALANCER) balancerActor: ActorRef)
                              (cc: ControllerComponents,
                               ws: WSClient)
                              (implicit val system: ActorSystem,
                               exc: ExecutionContext,
                               mat: Materializer,
                               configuration: Configuration) extends AbstractController(cc) {

  implicit val timeout: Timeout = configuration.get[Int]("common.actors.timeout").second

  val applicationUrl: URL = configuration.get[String]("common.host")

  def startOrStop(token: Token, host: URL): Action[AnyContent] = Action.async {
    if (token.isValid) {
      ws
        .url(host + "?" + List("token=" + token, "host=" + applicationUrl).mkString("&"))
        .get()
        .map {
          case wsResponse if wsResponse.status == 200 =>
            Ok(
              Succ().toJson
            )
          case wsResponse =>
            BadRequest(
              wsResponse.json
            )
        }
        .recoverFatal {
          case ex =>
            logger.error("Balancer.controllers.MainController: Exception during send start request\n", ex)
            BadRequest(
              Fail.unknown.toJson
            )
        }
    } else {
      Future.successful(
        BadRequest(
          Fail.tokenNotValid.toJson
        )
      )
    }
  }

  def sendData: Action[SendData] = Action.async(parse.json[SendData]) {
    implicit request =>
      sendMessageToActor(request.body)
  }

  def addProcessor(token: String, host: String): Action[AnyContent] = Action.async {
    sendMessageToActor(Add(token, Processor(host)))
  }

  def sendMessageToActor[T](message: T): Future[Result] = {
    (balancerActor ? message)
      .map {
        case succ: Succ =>
          Ok(
            succ.toJson
          )
        case fail: Fail =>
          BadRequest(
            fail.toJson
          )
        case _ =>
          BadRequest(
            Fail.unknown.toJson
          )
      }
      .recoverFatal {
        case ex =>
          logger.error("Balancer.controllers.MainController: Exception during send addProcessor request\n", ex)
          BadRequest(
            Fail.unknown.toJson
          )
      }
  }

  def deleteProcessor(token: String, host: String): Action[AnyContent] = Action.async {
    sendMessageToActor(Delete(token, Processor(host)))
  }

  def ping = Action(Ok(Succ().toJson))

}
