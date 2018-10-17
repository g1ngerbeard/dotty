import scala.quoted._

import scala.tasty._

object Macros {

  implicit inline def printTree[T](x: => T): Unit =
    ~impl('(x))

  def impl[T](x: Expr[T])(implicit staging: StagingContext): Expr[Unit] = {
    import staging.reflection._

    val tree = x.unseal

    val treeStr = tree.show
    val treeTpeStr = tree.tpe.show

    '{
      println(~treeStr.toExpr)
      println(~treeTpeStr.toExpr)
      println()
    }
  }
}
