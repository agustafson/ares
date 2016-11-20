package redscaler.interpreter

trait ArgConverters {

  implicit lazy val stringArgConverter: ArgConverter[String] = _.toCharArray.map(_.toByte).toVector
  implicit lazy val intArgConverter: ArgConverter[Int]       = i => stringArgConverter(i.toString)

}

object ArgConverters extends ArgConverters
