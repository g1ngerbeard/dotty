import scala.quoted._

import scala.tasty._
import scala.tasty.util._

object Macros {

  val tb = Toolbox.make
  import tb.run

  inline def testMacro: Unit = ~impl

  def impl(implicit staging: StagingContext): Expr[Unit] = {
    // 2 is a lifted constant
    val show1 = power(2.toExpr, 3.0.toExpr).show
    val run1  = run(power(2.toExpr, 3.0.toExpr))

    // n is a lifted constant
    val n = 2
    val show2 = power(n.toExpr, 4.0.toExpr).show
    val run2  = run(power(n.toExpr, 4.0.toExpr))

    // n is a constant in a quote
    val show3 = power('(2), 5.0.toExpr).show
    val run3 =  run(power('(2), 5.0.toExpr))

    // n2 is clearly not a constant
    // FIXME
//    val n2 = '{ println("foo"); 2 }
//    val show4 = show(power(n2, 6.0.toExpr))
//    val run4  = run(power(n2, 6.0.toExpr))

    '{
      println(~show1.toExpr)
      println(~run1.toExpr)
      println()
      println(~show2.toExpr)
      println(~run2.toExpr)
      println()
      println(~show3.toExpr)
      println(~run3.toExpr)
//      println()
//      println(~show4.toExpr)
//      println(~run4.toExpr)
    }
  }

  def power(n: Expr[Int], x: Expr[Double])(implicit staging: StagingContext): Expr[Double] = {
    import staging.reflection._

    val Constant = new ConstantExtractor(staging.reflection)
    n match {
      case Constant(n1) => powerCode(n1, x)
      case _ => '{ dynamicPower(~n, ~x) }
    }
  }

  def powerCode(n: Int, x: Expr[Double]): Staged[Double] =
    if (n == 0) '(1.0)
    else if (n == 1) x
    else if (n % 2 == 0) '{ { val y = ~x * ~x; ~powerCode(n / 2, '(y)) } }
    else '{ ~x * ~powerCode(n - 1, x) }

  def dynamicPower(n: Int, x: Double): Double =
    if (n == 0) 1.0
    else if (n % 2 == 0) dynamicPower(n / 2, x * x)
    else x * dynamicPower(n - 1, x)
}
