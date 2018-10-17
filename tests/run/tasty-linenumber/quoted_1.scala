import scala.quoted._

import scala.tasty._

class LineNumber(val value: Int) {
  override def toString: String = value.toString
}

object LineNumber {

  implicit inline def line[T >: Unit <: Unit]: LineNumber =
    ~lineImpl('[T])

  def lineImpl(x: Type[Unit])(implicit tasty: Tasty): Staged[LineNumber] = {
    import tasty._
    '(new LineNumber(~rootPosition.startLine.toExpr))
  }

}
