package actors

import akka.actor._
import akka.stream.Materializer
import akka.util.Timeout
import com.google.inject.Inject
import helpers.StreamQueueHelper
import helpers.implicits._
import models.internal._
import models.public._
import play.api.ConfigLoader._
import play.api.Configuration
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class BalancerActor @Inject()(streamQueueHelper: StreamQueueHelper,
                              ws: WSClient)
                             (implicit executionContext: ExecutionContext,
                              configuration: Configuration,
                              materializer: Materializer,
                              actorSystem: ActorSystem) extends Actor {

  implicit val timeout: Timeout = configuration.get[Int]("common.actors.timeout").second


  def actorStarted(availableProcessors: Set[Processor]): Actor.Receive = {
    case Add(token, processor) if token.isValid =>
      context.become(actorStarted(availableProcessors + processor))
      sender() ! Succ()

    case Delete(token, _) if token.isValid && availableProcessors.size == 1 =>
      context.become(receive)
      sender() ! Succ()

    case Delete(token, processor) if token.isValid =>
      context.become(actorStarted(availableProcessors - processor))
      sender() ! Succ()

    case sendData@SendData(token, _) if token.isValid =>
      val processor = availableProcessors.head
      self ! Delete(token, processor)
      streamQueueHelper.addQuery(sendData, processor)
      sender() ! Succ()

    case Add(token, _) if !token.isValid =>
      sender() ! Fail.tokenNotValid

    case Delete(token, _) if !token.isValid =>
      sender() ! Fail.tokenNotValid

    case SendData(token, _) if !token.isValid =>
      sender() ! Fail.tokenNotValid

    case _ =>
      sender() ! Fail.unknown

  }

  override def receive: Actor.Receive = {

    case Add(token, processor) if token.isValid =>
      context.become(actorStarted(Set(processor)))
      sender() ! Succ()
    case Delete(token, _) if !token.isValid =>
      sender() ! Fail.tokenNotValid
    case Add(token, _) if !token.isValid =>
      sender() ! Fail.tokenNotValid
    case sendData@SendData(token, _) if token.isValid =>
      streamQueueHelper.cacheQuery(sendData)
      sender() ! Succ()
    case _ =>
      sender() ! Fail.unknown

  }
}
