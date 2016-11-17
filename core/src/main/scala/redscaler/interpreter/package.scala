package redscaler

package object interpreter {
  type ArgConverter[T] = T => Vector[Byte]

  implicit class ByteVectorW(val bytes: Vector[Byte]) extends AnyVal {
    def asString: String = new String(bytes.toArray)
  }

}
