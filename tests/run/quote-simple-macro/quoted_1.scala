import scala.quoted._

object Macros {
  inline def foo(inline i: Int, dummy: Int, j: Int): Int = ~bar(i, '(j))
  def bar(x: Int, y: Expr[Int]): Staged[Int] = '{ ~x.toExpr + ~y }
}
