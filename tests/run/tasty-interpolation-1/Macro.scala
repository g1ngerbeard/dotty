
import scala.quoted._

import scala.language.implicitConversions
import scala.quoted.Exprs.LiftedExpr

object Macro {

  class StringContextOps(strCtx: => StringContext) {
    inline def s2(args: Any*): String = ~SIntepolator('(strCtx), '(args))
    inline def raw2(args: Any*): String = ~RawIntepolator('(strCtx), '(args))
    inline def foo(args: Any*): String = ~FooIntepolator('(strCtx), '(args))
  }
  implicit inline def SCOps(strCtx: => StringContext): StringContextOps = new StringContextOps(strCtx)
}

object SIntepolator extends MacroStringInterpolator[String] {
  protected def interpolate(strCtx: StringContext, args: List[Expr[Any]]): Staged[String] =
    '((~strCtx.toExpr).s(~args.toExprOfList: _*))
}

object RawIntepolator extends MacroStringInterpolator[String] {
  protected def interpolate(strCtx: StringContext, args: List[Expr[Any]]): Staged[String] =
    '((~strCtx.toExpr).raw(~args.toExprOfList: _*))
}

object FooIntepolator extends MacroStringInterpolator[String] {
  protected def interpolate(strCtx: StringContext, args: List[Expr[Any]]): Staged[String] =
    '((~strCtx.toExpr).s(~args.map(_ => '("foo")).toExprOfList: _*))
}

// TODO put this class in the stdlib or separate project?
abstract class MacroStringInterpolator[T] {

  final def apply(strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]]): Staged[T] = {
    try interpolate(strCtxExpr, argsExpr)
    catch {
      case ex: NotStaticlyKnownError =>
        // TODO use ex.expr to recover the position
        throw new QuoteError(ex.getMessage)
      case ex: StringContextError =>
        // TODO use ex.idx to recover the position
        throw new QuoteError(ex.getMessage)
      case ex: ArgumentError =>
        // TODO use ex.idx to recover the position
        throw new QuoteError(ex.getMessage)
    }
  }

  protected def interpolate(strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]]): Staged[T] =
    interpolate(getStaticStringContext(strCtxExpr), getArgsList(argsExpr))

  protected def interpolate(strCtx: StringContext, argExprs: List[Expr[Any]]): Staged[T]

  protected def getStaticStringContext(strCtxExpr: Expr[StringContext])(implicit st: StagingContext): StringContext = {
    import st.reflection._
    strCtxExpr.unseal.underlyingArgument match {
      case Term.Select(Term.Typed(Term.Apply(_, List(Term.Apply(_, List(Term.Typed(Term.Repeated(strCtxArgTrees), TypeTree.Inferred()))))), _), _) =>
        val strCtxArgs = strCtxArgTrees.map {
          case Term.Literal(Constant.String(str)) => str
          case tree => throw new NotStaticlyKnownError("Expected statically known StringContext", tree.seal[Any])
        }
        StringContext(strCtxArgs: _*)
      case tree =>
        throw new NotStaticlyKnownError("Expected statically known StringContext", tree.seal[Any])
    }
  }

  protected def getArgsList(argsExpr: Expr[Seq[Any]])(implicit st: StagingContext): List[Expr[Any]] = {
    import st.reflection._
    argsExpr.unseal.underlyingArgument match {
      case Term.Typed(Term.Repeated(args), _) => args.map(_.seal[Any])
      case tree => throw new NotStaticlyKnownError("Expected statically known argument list", tree.seal[Any])
    }
  }

  protected implicit def StringContextIsLiftable: Liftable[StringContext] = new Liftable[StringContext] {
    def toExpr(strCtx: StringContext)(implicit st: StagingContext): Expr[StringContext] = {
      // TODO define in stdlib?
      implicit def ListIsLiftable: Liftable[List[String]] = new Liftable[List[String]] {
        override def toExpr(list: List[String])(implicit st: StagingContext): Expr[List[String]] = list match {
          case x :: xs => '(~x.toExpr :: ~toExpr(xs))
          case Nil => '(Nil)
        }
      }
      '(StringContext(~strCtx.parts.toList.toExpr: _*))
    }
  }

  protected class NotStaticlyKnownError(msg: String, expr: Expr[Any]) extends Exception(msg)
  protected class StringContextError(msg: String, idx: Int, start: Int = -1, end: Int = -1) extends Exception(msg)
  protected class ArgumentError(msg: String, idx: Int) extends Exception(msg)

}
