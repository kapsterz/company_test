package controllers

import javax.inject._

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import models.public._
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.ExecutionContext
import protocols.actors._
import akka.pattern._
import akka.util.Timeout
import models.internal.{Fail, Succ}
import helpers.implicits._
import play.api.Configuration
import scala.concurrent.duration._

@Singleton
class MainController @Inject()(@Named(GENERATOR) generatorActor: ActorRef)
                              (cc: ControllerComponents, configuration: Configuration)
                              (implicit val system: ActorSystem,
                               exc: ExecutionContext,
                               mat: Materializer) extends AbstractController(cc) {

  implicit val timeout: Timeout = configuration.get[Int]("common.actors.timeout").second

  def start(token: String, host: String): Action[AnyContent] = Action.async {
    (generatorActor ? Start(token, host))
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
  }

  def stop(token: String): Action[AnyContent] = Action.async {
    (generatorActor ? Stop(token))
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
  }
// TODO: implement route to get data
//  def getData(token: String): Action[AnyContent] = ???

}
