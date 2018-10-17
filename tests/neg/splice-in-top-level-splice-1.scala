import scala.quoted._

object Foo {
  inline def foo(): Int = ~bar(~x) // error
  def x: Staged[Int] = '(1)
  def bar(i: Int): Staged[Int] = i.toExpr
}
