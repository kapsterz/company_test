package helpers

import models.public.Token
import play.api.libs.json.{JsObject, JsValue, Reads, Writes}
import play.api.{ConfigLoader, Configuration, Logger}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

package object implicits {
  implicit val logger: Logger = Logger("Generator")

  implicit class StringImplicits(string: String) {

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
        Future.successful(Await.result(s, Duration.Inf))
      } catch {
        pf
      }
    }
  }

  implicit class JsonWrites[T](obj: T) {
    def toJson(implicit format: Writes[T]): JsValue = format.writes(obj)
  }

  implicit class JsonReads[T](obj: JsObject) {
    def toObj(implicit format: Reads[T]): Option[T] = format.reads(obj).asOpt
  }

}
