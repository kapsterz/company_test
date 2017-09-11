package helpers

import javax.inject.Named

import akka.actor.ActorRef
import akka.stream.QueueOfferResult.Enqueued
import akka.stream.scaladsl.{Flow, Sink, SourceQueueWithComplete, Source => AkkaSrc}
import akka.stream.{Materializer, OverflowStrategy}
import akka.{Done, NotUsed}
import com.google.inject.Inject
import helpers.implicits._
import models.internal._
import models.public._
import play.api.Configuration
import play.api.libs.ws.WSClient
import protocols.actors.PROCESSOR

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class ProcessorHelper @Inject()(@Named(PROCESSOR) processorActor: ActorRef)
                               (kafkaProducerHelper: KafkaProducerHelper,
                                ws: WSClient)
                               (implicit configuration: Configuration,
                                executionContext: ExecutionContext,
                                materializer: Materializer) {

  val processorParallelism: Int = configuration.get[Int]("common.processor.parallelism")

  val dataCollectorConf: URL = configuration.get[String]("common.dataCollector.topic")

  val balancerUrl: URL = configuration.get[String]("common.host")

  val queueBufferSize: Int = configuration.get[Int]("common.queue.bufferSize")

  val queueTryCount: Int = 100

  val queueBatchSize: Int = configuration.get[Int]("common.queue.batchSize")


  val executionFlow: Flow[List[SendData], Done, NotUsed] =
    Flow[List[SendData]]
      .mapConcat(identity)
      .mapAsync(processorParallelism) {
        executeSendData
      }
      .grouped(queueBatchSize)
      .mapAsync(processorParallelism / 10) { sendData =>
        kafkaProducerHelper.sendToKafka(MeterId.generate, sendData, Processor(dataCollectorConf))
          .map(_ => Done)
          .recoverWithFatal {
            case ex =>
              logger.error("Processor.heplers.ProcessorHelper: Error during sending data\n" + ex)
              ws
                .url(balancerUrl)
                .post(
                  sendData.toJson
                )
                .map { _ =>
                  Done
                }
                .recoverWithFatal {
                  case _ =>
                    logger.error("Processor.heplers.ProcessorHelper: Error during sending data\n" + ex)
                    Future(
                      addToQueue(sendData.toList)
                    )
                      .map { _ =>
                        Done
                      }
                }
          }
      }
  val streamQueue: SourceQueueWithComplete[List[SendData]] = AkkaSrc
    .queue[List[SendData]](queueBufferSize, OverflowStrategy.backpressure)
    .via(executionFlow)
    .to(Sink.ignore)
    .run()

  def addToQueue(sendData: List[SendData], _try: Int = 0): Done = Await.result(streamQueue.offer(sendData), Duration.Inf) match {
    case Enqueued =>
      Done
    case _ if _try < queueTryCount =>
      addToQueue(sendData, _try + 1)
  }

  def executeSendData(sendData: SendData): Future[SendData] = {
    Future {
      Thread.sleep(10)
      sendData.copy(
        data = sendData.data.toUpperCase()
      )
    }
  }

}
