package helpers

import akka.NotUsed
import akka.stream.scaladsl.Source
import models.public._
import play.api.libs.json.{JsObject, JsValue, Reads, Writes}
import play.api.{ConfigLoader, Configuration, Logger}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Random, Try}

package object implicits {
  implicit val logger: Logger = Logger("Processor")
  implicit val topic: Topic = Random.nextString(10)

  implicit class IterableToSource[T](itarable: Iterable[T]) {
    def akkaSrc: Source[T, NotUsed] = Source.fromIterator(() => itarable.toIterator)
  }

  implicit class TokenImplicits(token: Token) {
    def isValid(implicit configuration: Configuration, configLoader: ConfigLoader[String], logger: Logger): Boolean = {
      val validToken = Try(configuration.get[String]("common.security.token")).toOption
      validToken.map(_ == token).getOrElse {
        logger.info("From Generator.helpers.implicits.TokenImplicits: Token Not Found in configuration file")
        false
      }
    }
  }

  implicit class RecoveryFatal[T](s: Future[T]) {
    def recoverFatal(pf: PartialFunction[Throwable, T])(implicit executor: ExecutionContext): Future[T] = {
      Future {
        try {
          Await.result(s, Duration.Inf)
        } catch {
          pf
        }
      }
    }

    def recoverWithFatal(pf: PartialFunction[Throwable, Future[T]])(implicit executor: ExecutionContext): Future[T] = {
      try {
        Future(Await.result(s, Duration.Inf))
      } catch {
        pf
      }
    }
  }

  implicit class JsonWrites[T](obj: T) {
    def toJson(implicit format: Writes[T]): JsValue = format.writes(obj)
  }

  implicit class JsValueReads[T](obj: JsValue) {
    def toObj(implicit format: Reads[T]): Option[T] = format.reads(obj).asOpt
  }

  implicit class JsObjectReads[T](obj: JsObject) {
    def toObj(implicit format: Reads[T]): Option[T] = format.reads(obj).asOpt
  }

}
