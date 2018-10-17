
import UnrolledExpr._

import scala.reflect.ClassTag
import scala.quoted._

object Lifters {

  implicit def ClassTagIsLiftable[T : Type](implicit ct: ClassTag[T]): Liftable[ClassTag[T]] = new Liftable[ClassTag[T]] {
    override def toExpr(ct: ClassTag[T])(implicit st: StagingContext): Expr[ClassTag[T]] = '(ClassTag(~ct.runtimeClass.toExpr))
  }

  implicit def ArrayIsLiftable[T : Type: ClassTag](implicit l: Liftable[T]): Liftable[Array[T]] = new Liftable[Array[T]] {
    override def toExpr(arr: Array[T])(implicit st: StagingContext): Expr[Array[T]] = '{
      val array = new Array[T](~arr.length.toExpr)(~implicitly[ClassTag[T]].toExpr)
      ~initArray(arr, '(array))
    }
  }

  implicit def IntArrayIsLiftable: Liftable[Array[Int]] = new Liftable[Array[Int]] {
    override def toExpr(arr: Array[Int])(implicit st: StagingContext): Expr[Array[Int]] = '{
      val array = new Array[Int](~arr.length.toExpr)
      ~initArray(arr, '(array))
    }
  }

  private def initArray[T : Liftable](arr: Array[T], array: Expr[Array[T]])(implicit st: StagingContext): Expr[Array[T]] = {
    UnrolledExpr.block(
      arr.zipWithIndex.map {
        case (x, i) => '{ (~array)(~i.toExpr) = ~x.toExpr }
      }.toList,
      array)
  }

}
