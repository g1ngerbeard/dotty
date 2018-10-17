
import scala.quoted._

object Test {
  def main(args: Array[String]): Unit = {
    val tb: Toolbox = Toolbox.make

    def lambdaExpr: Staged[Int => Unit] = '{
      (x: Int) => println("lambda(" + x + ")")
    }
    println()

    val lambda = tb.run(lambdaExpr)
    lambda(4)
    lambda(5)
  }
}
