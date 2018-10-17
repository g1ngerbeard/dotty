import scala.quoted._

class Foo {
  def foo: Staged[Unit] = {
    val e: Expr[Int] = '(3)
    val f: Expr[Int] = '(5)
    val t: Type[Int] = '[Int]
    val q = '{ ~( '{ (~e + ~f).asInstanceOf[~t] } ) }
    '{ println(~q.show.toExpr) }
  }
}

object Test {
  def main(args: Array[String]): Unit = {
    val tb = Toolbox.make
    tb.run {
      val f = new Foo
      f.foo
    }
  }
}
