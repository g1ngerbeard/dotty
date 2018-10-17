import scala.quoted._

object Test {
  def main(args: Array[String]): Unit = {
    val tb = Toolbox.make

    def x: Staged[Int] = '(3)

    def f3: Staged[Int => Int] = '{
      val f: (x: Int) => Int = x => x + x
      f
    }
    println(tb.run(f3(implicitly)(x)))
    println(tb.show(f3(implicitly)(x))) // TODO improve printer
  }
}
