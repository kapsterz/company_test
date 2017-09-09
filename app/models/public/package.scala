package models

package object public {

  type Token = String

  type URL = String

  type Data = String

  case class Start(token: Token, url: URL)

  case class Stop(token: Token)

}
