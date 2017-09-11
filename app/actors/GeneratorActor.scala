package actors

import akka.Done
import akka.actor.{Actor, ActorSystem}
import akka.stream.{Materializer, OverflowStrategy}
import com.google.inject.Inject
import helpers.GeneratorHelper
import models.public._
import models.internal._
import helpers.implicits._
import play.api.Configuration
import play.api.ConfigLoader._
import play.api.libs.ws.WSClient
import akka.stream.scaladsl.{Sink, SourceQueueWithComplete, Source => AkkaSrc}
import akka.util.Timeout

import scala.concurrent.{ExecutionContext}
import scala.concurrent.duration._

class GeneratorActor @Inject()(generatorHelper: GeneratorHelper,
                               ws: WSClient)
                              (implicit executionContext: ExecutionContext,
                               configuration: Configuration,
                               materializer: Materializer,
                               actorSystem: ActorSystem) extends Actor {
  implicit val timeout: Timeout = configuration.get[Int]("common.actors.timeout").second

  val queueBufferSize: Int = configuration.get[Int]("common.queue.bufferSize")

  val queueParallelism: Int = configuration.get[Int]("common.queue.parallelism")

  val schedulerTimeout: Int = configuration.get[Int]("common.scheduler.timeout")

  val streamQueue: SourceQueueWithComplete[(SendData, URL)] = AkkaSrc
    .queue[(SendData, URL)](queueBufferSize, OverflowStrategy.backpressure)
    .mapAsync(queueParallelism) { case (sendData, url) =>
      ws
        .url(url)
        .post(
          sendData.toJson
        )
        .map(_ => Done)
        .recoverFatal {
          case ex =>
            logger.error("Generator.actors.GeneratorActor: Error during sending data\n" + ex)
            Done
        }
    }
    .to(Sink.ignore)
    .run()

  def actorStarted(currentUrl: URL): Actor.Receive = {
    case start@Start(token, _) if token.isValid =>
      context.become(receive)
      self.tell(start, sender())
    case Stop(token) if token.isValid =>
      context.become(receive)
      sender() ! Succ()
    case sendData@SendData(token, _) if token.isValid =>
      streamQueue.offer(sendData, currentUrl)
      context
        .system
        .scheduler
        .scheduleOnce(
          schedulerTimeout.milli,
          self,
          SendData(token, generatorHelper.createQuery)
        )
    case Start(token, _) if !token.isValid =>
      sender() ! Fail.tokenNotValid

    case Stop(token) if !token.isValid =>
      sender() ! Fail.tokenNotValid

    case _ =>
      sender() ! Fail.unknown
  }

  override def receive: Actor.Receive = {

    case Start(token, url) if token.isValid =>
      context.become(actorStarted(url))
      context
        .system
        .scheduler
        .scheduleOnce(
          schedulerTimeout.milli,
          self,
          SendData(token, generatorHelper.createQuery)
        )
      sender() ! Succ()
    case Start(token, _) if !token.isValid =>
      sender() ! Fail.tokenNotValid

    case Stop(token) if !token.isValid =>
      sender() ! Fail.tokenNotValid

    case _ =>
      sender() ! Fail.unknown

  }
}
