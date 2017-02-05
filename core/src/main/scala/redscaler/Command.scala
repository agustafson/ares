package redscaler

import redscaler.interpreter.ArgConverters.stringArgConverter

case class Command(commandName: String, args: Seq[Vector[Byte]])

object Command {
  def keyCommand(commandName: String, key: String, args: Seq[Vector[Byte]]) =
    Command(commandName, stringArgConverter(key) +: args)

  def noArgCommand(commandName: String) = Command(commandName, Seq.empty)
}
