package dotty.tools.dotc.tastyreflect

import dotty.tools.dotc.ast.{Trees, tpd}
import dotty.tools.dotc.core.Contexts
import dotty.tools.dotc.core.Decorators._

trait PatternOpsImpl extends scala.tasty.reflect.PatternOps with CoreImpl {

  def ValueDeco(value: Value): Pattern.ValueAPI = new Pattern.ValueAPI {
    def value(implicit ctx: Context): Term = ???
  }
  def BindDeco(bind: Bind): Pattern.BindAPI = new Pattern.BindAPI {
    def name(implicit ctx: Context): String = ???
    def pattern(implicit ctx: Context): Pattern = ???
  }
  def UnapplyDeco(unapply: Unapply): Pattern.UnapplyAPI = new Pattern.UnapplyAPI {
    def fun(implicit ctx: Context): Term = unapply.fun
    def implicits(implicit ctx: Context): List[Term] = unapply.implicits
    def patterns(implicit ctx: Context): List[Pattern] = effectivePatterns(unapply.patterns)

    private def effectivePatterns(patterns: List[Pattern]): List[Pattern] = patterns match {
      case patterns0 :+ Trees.SeqLiteral(elems, _) => patterns0 ::: elems
      case _ => patterns
    }
  }
  def AlternativeDeco(alternatives: Alternatives): Pattern.AlternativesAPI = new Pattern.AlternativesAPI {
    def patterns(implicit ctx: Context): List[Pattern] = alternatives.trees
  }
  def TypeTestDeco(typeTest: TypeTest): Pattern.TypeTestAPI = new Pattern.TypeTestAPI {
    def tpt(implicit ctx: Context): TypeTree = typeTest.tpt
  }

  // ----- Patterns -------------------------------------------------

  def PatternDeco(pattern: Pattern): PatternAPI = new PatternAPI {
    def pos(implicit ctx: Context): Position = pattern.pos
    def tpe(implicit ctx: Context): Type = pattern.tpe.stripTypeVar
  }

  object Pattern extends PatternModule {

    object IsValue extends IsValueModule {
      def unapply(pattern: Pattern)(implicit ctx: Context): Option[Value] = pattern match {
        case lit: tpd.Literal => Some(lit)
        case ref: tpd.RefTree if ref.isTerm => Some(ref)
        case ths: tpd.This => Some(ths)
        case _ => None
      }
    }

    object Value extends ValueModule {
      def apply(tpt: Term): Value = ???
      def copy(original: Value)(tpt: Term): Value = ???
      def unapply(x: Pattern)(implicit ctx: Context): Option[Term] = IsValue.unapply(x)
    }

    object IsBind extends IsBindModule {
      def unapply(x: Pattern)(implicit ctx: Context): Option[Bind] = x match {
        case x: tpd.Bind if x.name.isTermName => Some(x)
        case _ => None
      }
    }

    object Bind extends BindModule {
      def apply(name: String, pattern: Pattern): Bind = ???
      def copy(original: Bind)(name: String, pattern: Pattern): Bind = ???
      def unapply(pattern: Pattern)(implicit ctx: Context): Option[(String, Pattern)] = pattern match {
        case IsBind(pattern) => Some((pattern.name.toString, pattern.body))
        case _ => None
      }
    }

    object IsUnapply extends IsUnapplyModule {
      def unapply(pattern: Pattern)(implicit ctx: Context): Option[Unapply] = pattern match {
        case pattern @ Trees.UnApply(_, _, _) => Some(pattern)
        case Trees.Typed(pattern @ Trees.UnApply(_, _, _), _) => Some(pattern)
        case _ => None
      }
    }

    object Unapply extends UnapplyModule {
      def apply(fun: Term, implicits: List[Term], patterns: List[Pattern]): Unapply = ???
      def copy(original: Unapply)(fun: Term, implicits: List[Term], patterns: List[Pattern]): Unapply =
        tpd.cpy.UnApply(original)(fun, implicits, patterns)
      def unapply(x: Pattern)(implicit ctx: Context): Option[(Term, List[Term], List[Pattern])] = x match {
        case IsUnapply(x) => Some((x.fun, x.implicits, UnapplyDeco(x).patterns))
        case _ => None
      }
    }

    object IsAlternatives extends IsAlternativesModule {
      def unapply(pattern: Pattern)(implicit ctx: Context): Option[Alternatives] = pattern match {
        case pattern: tpd.Alternative => Some(pattern)
        case _ => None
      }
    }

    object Alternatives extends AlternativesModule {
      def apply(patterns: List[Pattern]): Alternatives = ???
      def copy(original: Alternatives)(patterns: List[Pattern]): Alternatives =
        tpd.cpy.Alternative(original)(patterns)
      def unapply(x: Pattern)(implicit ctx: Context): Option[List[Pattern]] = x match {
        case x: tpd.Alternative => Some(x.trees)
        case _ => None
      }
    }

    object IsTypeTest extends IsTypeTestModule {
      def unapply(pattern: Pattern)(implicit ctx: Context): Option[TypeTest] = ???
    }

    object TypeTest extends TypeTestModule {
      def apply(tpt: List[TypeTree]): TypeTest = ???
      def copy(original: TypeTest)(tpt: List[TypeTree]): TypeTest = ???
      def unapply(x: Pattern)(implicit ctx: Context): Option[TypeTree] = x match {
        case Trees.Typed(Trees.UnApply(_, _, _), _) => None
        case Trees.Typed(_, tpt) => Some(tpt)
        case _ => None
      }
    }

  }

}
