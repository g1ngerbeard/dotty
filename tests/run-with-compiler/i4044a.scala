import scala.quoted._

class Foo {
  def foo: Staged[Any] = {
    val e: Expr[Int] = '(3)
    val q = '{ ~( '{ ~e } ) }
    q
  }
}

object Test {
  def main(args: Array[String]): Unit = {
    val tb = Toolbox.make
    println(tb.show {
      val f = new Foo
      f.foo
    })
  }
}
