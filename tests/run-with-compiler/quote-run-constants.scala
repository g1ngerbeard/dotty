
import scala.quoted._

object Test {
  def main(args: Array[String]): Unit = {
    val tb = Toolbox.make
    import tb._

    println(run(true.toExpr))
    println(run('a'.toExpr))
    println(run('\n'.toExpr))
    println(run('"'.toExpr))
    println(run('\''.toExpr))
    println(run('\\'.toExpr))
    println(run(1.toExpr))
    println(run(2.toExpr))
    println(run(3L.toExpr))
    println(run(4.0f.toExpr))
    println(run(5.0d.toExpr))
    println(run("xyz".toExpr))

    println("======")

    println(show(true.toExpr))
    println(show('a'.toExpr))
    println(show('\n'.toExpr))
    println(show('"'.toExpr))
    println(show('\''.toExpr))
    println(show('\\'.toExpr))
    println(show(1.toExpr))
    println(show(2.toExpr))
    println(show(3L.toExpr))
    println(show(4.0f.toExpr))
    println(show(5.0d.toExpr))
    println(show("xyz".toExpr))
    println(show("\n\\\"'".toExpr))
    println(show("""abc
         xyz""".toExpr))
  }
}
