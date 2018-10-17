import scala.quoted._

case class Location(owners: List[String])

object Location {

  implicit inline def location: Location = ~impl

  def impl: Staged[Location] = {
    val list = List("a", "b", "c", "d", "e", "f")
    '(new Location(~list.toExpr))
  }

  private implicit def ListIsLiftable[T : Liftable : Type]: Liftable[List[T]] = new Liftable[List[T]] {
    def toExpr(x: List[T])(implicit st: StagingContext): Expr[List[T]] = x match {
      case x :: xs  => '{ ~x.toExpr :: ~xs.toExpr }
      case Nil => '{ List.empty[T] }
    }
  }
}
