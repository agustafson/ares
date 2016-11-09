package ares

package object interpreter {

  implicit class ByteVectorW(val bytes: Vector[Byte]) extends AnyVal {
    def asString: String = new String(bytes.toArray)
  }

}
