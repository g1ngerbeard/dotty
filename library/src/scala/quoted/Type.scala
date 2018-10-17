package scala.quoted

import scala.quoted.Types.TaggedType
import scala.reflect.ClassTag

sealed abstract class Type[T] {
  type unary_~ = T

  def show(implicit st: StagingContext): String = st.show(this)
}

/** Some basic type tags, currently incomplete */
object Type {
  /** A term quote is desugared by the compiler into a call to this method */
  def apply[T]: Type[T] =
    throw new Error("Internal error: this method call should have been replaced by the compiler")

  implicit def UnitTag: Type[Unit] = new TaggedType[Unit]
  implicit def BooleanTag: Type[Boolean] = new TaggedType[Boolean]
  implicit def ByteTag: Type[Byte] = new TaggedType[Byte]
  implicit def CharTag: Type[Char] = new TaggedType[Char]
  implicit def ShortTag: Type[Short] = new TaggedType[Short]
  implicit def IntTag: Type[Int] = new TaggedType[Int]
  implicit def LongTag: Type[Long] = new TaggedType[Long]
  implicit def FloatTag: Type[Float] = new TaggedType[Float]
  implicit def DoubleTag: Type[Double] = new TaggedType[Double]
}

/** All implementations of Type[T].
 *  These should never be used directly.
 */
object Types {

  /** An Type backed by a value */
  final class TaggedType[T](implicit val ct: ClassTag[T]) extends Type[T] {
    override def toString: String = s"Type($ct)"
  }

  /** An Type backed by a tree */
  final class TreeType[Tree](val typeTree: Tree) extends quoted.Type[Any] {
    override def toString: String = s"Type(<tasty tree>)"
  }
}
