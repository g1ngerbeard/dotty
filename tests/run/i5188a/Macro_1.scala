import scala.quoted._

object Lib {
  inline def sum(inline args: Int*): Int = ~impl(args: _*)
  def impl(args: Int*): Staged[Int] = args.sum.toExpr
}
