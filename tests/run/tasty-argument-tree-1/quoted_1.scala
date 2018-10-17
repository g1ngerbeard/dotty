import scala.quoted._
import scala.tasty._

object Macros {

  inline def inspect[T](x: T): Unit = ~impl('(x))

  def impl[T](x: Expr[T])(implicit st: StagingContext): Expr[Unit] = {
    import st.reflection._
    val tree = x.unseal
    '{
      println()
      println("tree: " + ~tree.show.toExpr)
      println("tree deref. vals: " + ~tree.underlying.show.toExpr)
    }
  }
}
