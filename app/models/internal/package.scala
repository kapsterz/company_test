package models

import models.public._
import play.api.libs.json.{Json, OFormat}

package object internal {

  case class SendData(token: Token, data: Data)

  object SendData {
    implicit val format: OFormat[SendData] = Json.format[SendData]
  }

  case class Succ(error: Int = 0)

  object Succ {
    implicit val format: OFormat[Succ] = Json.format[Succ]
  }

  case class Fail(error: Int, message: String)

  object Fail {
    implicit val format: OFormat[Fail] = Json.format[Fail]
    val unknown = Fail(-1, "Unknown exception")
    val tokenNotValid = Fail(1, "Token not valid")
  }

}
