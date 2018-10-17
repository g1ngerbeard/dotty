import scala.quoted._

class Foo {
  def foo: Unit = {
    def q: Staged[Int] = '{ ~( '{ ~( '{ 5 } ) } ) }
    val tb = Toolbox.make
    println(tb.show(q))
  }
}

object Test {
  def main(args: Array[String]): Unit = {
    val f = new Foo
    f.foo
  }
}
