package ares.interpreter

import scala.languageFeature.experimental.macros

trait CommandWriter[T] {
  val asCommand: T => Array[Byte]
}

object CommandWriter {
  implicit def commandWriterForType[T]: CommandWriter[T] = macro CommandWriterMacros.commandWriterMacro[T]
}
