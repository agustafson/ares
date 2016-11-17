package redscaler.interpreter

trait ArgConverters {

  implicit lazy val stringArgConverter: ArgConverter[String] = _.toCharArray.map(_.toByte).toVector

}

object ArgConverters extends ArgConverters
