import scala.quoted._

object Macros {
  inline def foo(i: => Int): Int = ~fooImpl('(i))
  def fooImpl(i: Expr[Int]): Staged[Int] = {
    val tb = Toolbox.make
    val y: Int = tb.run(i)
    y.toExpr
  }
}
