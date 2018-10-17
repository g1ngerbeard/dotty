import scala.quoted._
import scala.tasty.Reflection

object Foo {

  type Macro[X] = implicit StagingContext => Expr[X]
  type Tastier[X] = implicit StagingContext => X

  implicit inline def foo: String =
    ~fooImpl

  def fooImpl(implicit staging: StagingContext): implicit StagingContext => Tastier[implicit StagingContext => Macro[String]] = {
    '("abc")
  }

}
