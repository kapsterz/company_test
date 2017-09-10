package models

import play.api.libs.json.Writes

package object public {

  type Token = String

  type URL = String

  type Data = String

  case class Start(token: Token, url: URL)

  case class Stop(token: Token)

  case class Add(token: Token, processor: Processor)

  case class Delete(token: Token, processor: Processor)

  case class Processor(url: URL)

}
