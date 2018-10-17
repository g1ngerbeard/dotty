import scala.quoted._

object Test {
  def main(args: Array[String]): Unit = {
    val tb = Toolbox.make

    def expr(i: Int): Staged[Int] = '{
      val a = 3 + ~i.toExpr
      2 + a
    }
    for (i <- 0 to 200)
      tb.run(expr(i))
  }
}
