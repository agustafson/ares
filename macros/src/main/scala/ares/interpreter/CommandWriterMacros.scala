package ares.interpreter

import ares.interpreter.RedisConstants._

import scala.reflect.macros.blackbox

object CommandWriterMacros {
  def commandWriterMacro[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[CommandWriter[T]] = {
    import c.universe._
    val baseType: c.universe.Type = weakTypeOf[T]

    // fields within the type T 
    val fields: Iterable[c.universe.MethodSymbol] = baseType.decls.collect {
      case methodSymbol: MethodSymbol if methodSymbol.isCaseAccessor => methodSymbol
    }
    // ensure all CommandWriters exist for field return types 
    val args: Vector[c.universe.Tree] = fields.toVector.map { field =>
      val classFieldName = field.name.decodedName.toTermName
      q"""
        import RedisConstants._
        val argConverter = implicitly[ares.interpreter.ArgConverter[${field.returnType}]]
        val argValue = argConverter(item.${classFieldName})
        (DOLLAR_BYTE +: intCrlf(argValue.length)) ++ argValue ++ CRLF
        """
    }
    val combinedArgs: c.universe.Tree = args.fold(q"Vector.empty") {
      case (acc, next) =>
        q"""$acc ++ $next"""
    }
    val commandName                        = baseType.dealias.toString.split('.').last
    val commandWriterType: c.universe.Type = weakTypeOf[CommandWriter[T]]
    val commandWriter: c.universe.Tree =
      q"""
        import RedisConstants._

        new $commandWriterType {
          val asCommand: $baseType => Array[Byte] = { (item: $baseType) =>
            val commandName = $commandName
            val bytes = new scala.collection.mutable.ListBuffer[Byte]() +=
              ASTERISK_BYTE ++= intCrlf(${args.length + 1}) +=
              DOLLAR_BYTE ++= intCrlf(commandName.length) ++=
              commandName.toArray.map(_.toByte) ++= CRLF ++=
              $combinedArgs
            bytes.result().toArray
          }
        }
       """

    c.Expr[CommandWriter[T]](commandWriter)
  }
}
