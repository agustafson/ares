package redscaler

package object interpreter {
  type ArgConverter[T] = T => Vector[Byte]
}
