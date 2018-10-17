
import scala.quoted._

object Macros {

  def impl(tup: Tuple1[Int]): Staged[Int] = tup._1.toExpr

  def impl2(tup: Tuple1[Tuple1[Int]]): Staged[Int] = impl(tup._1)

}
