import scala.quoted._
import scala.tasty.Reflection

object SourceFiles {

  type Macro[X] = implicit StagingContext => Expr[X]

  implicit inline def getThisFile: String =
    ~getThisFileImpl

  def getThisFileImpl: Macro[String] = {
    val staging = implicitly[StagingContext]
    import staging.reflection._
    rootContext.source.getFileName.toString.toExpr
  }

}
