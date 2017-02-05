package redscaler

trait ByteVector {
  implicit class ByteVectorW(val bytes: Vector[Byte]) {
    def asString: String = new String(bytes.toArray)
  }
}

object ByteVector extends ByteVector
