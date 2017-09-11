package models

import play.api.libs.json.{Json, OFormat, Writes}

import scala.util.Random

package object public {

  type Token = String

  type Topic = String


  type URL = String

  type Data = String

  case class Start(token: Token, url: URL)

  case class Stop(token: Token)

  case class Add(token: Token, processor: Processor)

  case class Delete(token: Token, processor: Processor)

  case class Processor(url: URL)

  object Add {
    implicit val format: OFormat[Add] = Json.format[Add]
  }

  case class AddRepaid(add: Add)

  object Processor {
    implicit val format: OFormat[Processor] = Json.format[Processor]
    def generate: Processor = Processor(Random.nextString(10))
  }

}
