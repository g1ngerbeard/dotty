
import scala.quoted._

object Macros {
  def impl(foo: Any): Staged[String] = foo.getClass.getCanonicalName.toExpr
}

case object Bar {
  case object Baz
}

package foo {
  case object Bar {
    case object Baz
  }
}
