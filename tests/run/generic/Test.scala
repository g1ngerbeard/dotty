import generic._
import Tree._
import List._
import java.io._
import Shapes._

object Test {
  import Serialization._

  private var lCount, tCount = 0

// ------- Code that will eventually be produced by macros -------------

  implicit def ListSerializable[Elem](implicit es: Serializable[Elem]): Serializable[List[Elem]] = {
    implicit lazy val lsElem: Serializable[List[Elem]] = {
      lCount += 1 // test code to verify we create bounded number of Serializables
      RecSerializable[List[Elem], List.Shape[Elem]]
    }
    lsElem
  }

  implicit def TreeSerializable[R]: Serializable[Tree[R]] = {
    implicit lazy val tR: Serializable[Tree[R]] = {
      tCount += 1 // test code to verify we create bounded number of Serializables
      RecSerializable[Tree[R], Tree.Shape[R]]
    }
    tR
  }
  implicit lazy val tsInt: Serializable[Tree[Int]] = TreeSerializable[Int]
  implicit lazy val tsBoolean: Serializable[Tree[Boolean]] = TreeSerializable[Boolean]

// ------- Test code --------------------------------------------------------

  /** Serialize data, then deserialize it back and check that it is the same. */
  def sds[D](data: D)(implicit ser: Serializable[D]) = {
    val outBytes = new ByteArrayOutputStream
    val out = new DataOutputStream(outBytes)
    ser.write(data, out)
    out.flush()
    val inBytes = new ByteArrayInputStream(outBytes.toByteArray)
    val in = new DataInputStream(inBytes)
    val result = ser.read(in)
    assert(data == result, s"$data != $result")
  }

  val data1 =
    Cons(1, Cons(2, Cons(3, Nil)))

  val data2 =
    If(IsZero(Pred(Succ(Zero))), Succ(Succ(Zero)), Pred(Pred(Zero)))

  def main(args: Array[String]) = {
    sds(data1)
    assert(lCount == 1, lCount)
    sds(data2)
    assert(tCount == 2, tCount)
    assert(implicitly[Values[Col]].values == collection.immutable.List(Red, Green, Blue))
  }
}
