package actors

import akka.actor.{Actor, ActorSystem}
import akka.stream.Materializer
import com.google.inject.Inject
import helpers.implicits._
import models.internal._
import models.public._
import play.api.Configuration
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class ProcessorActor @Inject()(ws: WSClient)
                              (implicit executionContext: ExecutionContext,
                               configuration: Configuration,
                               materializer: Materializer,
                               actorSystem: ActorSystem) extends Actor {
  //TODO: Move hard-coded values in conf file
  val balancerUrl: URL = configuration.get[String]("common.host")
  val timeoutActorRepaid: Int = 5

  override def receive: Receive = {
    case add@Add(token, _) if token.isValid =>
      context.become(available)
      self ! add
    case _ =>
      sender() ! Fail.unknown
  }

  def available: Receive = {
    case add@Add(token, _) if token.isValid =>
      ws
        .url(balancerUrl)
        .post(
          add.toJson
        )
        .andThen { case _ =>
          context.system.scheduler.scheduleOnce(timeoutActorRepaid.seconds, self, AddRepaid(add))
          sender() ! Succ()
        }
    case addRepaid@AddRepaid(add) if add.token.isValid =>
      ws
        .url(balancerUrl)
        .post(
          add.toJson
        )
        .andThen { case _ =>
          context.system.scheduler.scheduleOnce(timeoutActorRepaid.seconds, self, addRepaid)
          sender() ! Succ()
        }
    case Delete(token, _) =>
      context.become(receive)
      sender() ! Succ()
    case _ =>
      sender() ! Fail.unknown
  }
}
