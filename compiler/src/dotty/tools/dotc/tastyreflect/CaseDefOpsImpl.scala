package dotty.tools.dotc.tastyreflect

import dotty.tools.dotc.ast.tpd

trait CaseDefOpsImpl extends scala.tasty.reflect.CaseDefOps with CoreImpl with Helpers {

  def CaseDefDeco(caseDef: CaseDef): CaseDefAPI = new CaseDefAPI {
    def pattern(implicit ctx: Context): Pattern = caseDef.pat
    def guard(implicit ctx: Context): Option[Term] = optional(caseDef.guard)
    def rhs(implicit ctx: Context): Term = caseDef.body
  }

  object CaseDef extends CaseDefModule {
    def apply(pat: Pattern, guard: Option[Term], body: Term)(implicit ctx: Context): CaseDef = ???

    def copy(original: CaseDef)(pat: Pattern, guard: Option[Term], body: Term)(implicit ctx: Context): CaseDef =
      tpd.cpy.CaseDef(original)(pat, guard.getOrElse(tpd.EmptyTree), body)

    def unapply(x: CaseDef): Some[(Pattern, Option[Term], Term)] = Some(x.pat, optional(x.guard), x.body)
  }

  def TypeCaseDefDeco(caseDef: TypeCaseDef): TypeCaseDefAPI = new TypeCaseDefAPI {
    def pattern(implicit ctx: Context): Pattern = caseDef.pat
    def rhs(implicit ctx: Context): Term = caseDef.body
  }

  object TypeCaseDef extends TypeCaseDefModule {
    def unapply(x: TypeCaseDef): Some[(TypeTree, TypeTree)] = Some((x.pat, x.body))
  }
}
