
import scala.quoted._

object Macros {
  def sum(nums: Int*): Staged[Int] = nums.sum.toExpr
}
