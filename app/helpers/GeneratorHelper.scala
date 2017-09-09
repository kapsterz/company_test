package helpers

import models.public.Data

import scala.util.Random

class GeneratorHelper {
  def createQuery: Data = Random.nextString(10)
}
