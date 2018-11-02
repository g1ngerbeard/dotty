package dotty.tools.dotc
package transform

import dotty.tools.dotc.ast.{Trees, tpd}
import scala.collection.mutable
import ValueClasses.isMethodWithExtension
import core._
import Contexts._, Flags._, Symbols._, NameOps._, Trees._
import TypeUtils._, SymUtils._
import DenotTransformers.DenotTransformer
import Symbols._
import util.Positions._
import Decorators._
import NameKinds.SuperAccessorName

/** This class adds super accessors for all super calls that either
 *  appear in a trait or have as a target a member of some outer class.
 *
 *  It also checks that:
 *
 *  (1) Symbols accessed from super are not abstract, or are overridden by
 *  an abstract override.
 *
 *  (2) If a symbol accessed from super is defined in a real class (not a trait),
 *  there are no abstract members which override this member in Java's rules
 *  (see SI-4989; such an access would lead to illegal bytecode)
 *
 *  (3) Super calls do not go to some synthetic members of Any (see isDisallowed)
 *
 *  (4) Super calls do not go to synthetic field accessors
 */
class SuperAccessors(thisPhase: DenotTransformer) {

  import tpd._


    /** Some parts of trees will get a new owner in subsequent phases.
     *  These are value class methods, which will become extension methods.
     *  (By-name arguments used to be included also, but these
     *  don't get a new class anymore, they are just wrapped in a new method).
     *
     *  These regions will have to be treated specially for the purpose
     *  of adding accessors. For instance, super calls from these regions
     *  always have to go through an accessor.
     *
     *  The `invalidEnclClass` field, if different from NoSymbol,
     *  contains the symbol that is not a valid owner.
     */
    private[this] var invalidEnclClass: Symbol = NoSymbol

    private def withInvalidCurrentClass[A](trans: => A)(implicit ctx: ContextRenamed): A = {
      val saved = invalidEnclClass
      invalidEnclClass = ctx.owner
      try trans
      finally invalidEnclClass = saved
    }

    private def validCurrentClass(implicit ctx: ContextRenamed): Boolean =
      ctx.owner.enclosingClass != invalidEnclClass

    /** List buffers for new accessor definitions, indexed by class */
    private val accDefs = newMutableSymbolMap[mutable.ListBuffer[Tree]]

    /** A super accessor call corresponding to `sel` */
    private def superAccessorCall(sel: Select)(implicit ctx: ContextRenamed) = {
      val Select(qual, name) = sel
      val sym = sel.symbol
      val clazz = qual.symbol.asClass
      var superName = SuperAccessorName(name.asTermName)
      if (clazz is Trait) superName = superName.expandedName(clazz)
      val superInfo = sel.tpe.widenSingleton.ensureMethodic

      val accPos = sel.pos.focus
      val superAcc = clazz.info.decl(superName)
        .suchThat(_.signature == superInfo.signature).symbol
        .orElse {
          ctx.debuglog(s"add super acc ${sym.showLocated} to $clazz")
          val maybeDeferred = if (clazz is Trait) Deferred else EmptyFlags
          val acc = ctx.newSymbol(
              clazz, superName, Artifact | Method | maybeDeferred,
              superInfo, coord = accPos).enteredAfter(thisPhase)
          // Diagnostic for SI-7091
          if (!accDefs.contains(clazz))
            ctx.error(s"Internal error: unable to store accessor definition in ${clazz}. clazz.hasPackageFlag=${clazz is Package}. Accessor required for ${sel} (${sel.show})", sel.pos)
          else accDefs(clazz) += DefDef(acc, EmptyTree).withPos(accPos)
          acc
        }

      This(clazz).select(superAcc).withPos(sel.pos)
    }

    /** Check selection `super.f` for conforming to rules. If necessary,
     *  replace by a super accessor call.
     */
    private def transformSuperSelect(sel: Select)(implicit ctx: ContextRenamed): Tree = {
      val Select(sup @ Super(_, mix), name) = sel
      val sym   = sel.symbol
      assert(sup.symbol.exists, s"missing symbol in $sel: ${sup.tpe}")
      val clazz = sup.symbol

      if (sym.isTerm && !sym.is(Method, butNot = Accessor) && !ctx.owner.is(ParamForwarder))
        // ParamForwaders as installed ParamForwarding.scala do use super calls to vals
        ctx.error(s"super may be not be used on ${sym.underlyingSymbol}", sel.pos)
      else if (isDisallowed(sym))
        ctx.error(s"super not allowed here: use this.${sel.name} instead", sel.pos)
      else if (sym is Deferred) {
        val member = sym.overridingSymbol(clazz.asClass)
        if (!mix.name.isEmpty ||
            !member.exists ||
            !((member is AbsOverride) && member.isIncompleteIn(clazz)))
          ctx.error(
              i"${sym.showLocated} is accessed from super. It may not be abstract unless it is overridden by a member declared `abstract' and `override'",
              sel.pos)
        else ctx.log(i"ok super $sel ${sym.showLocated} $member $clazz ${member.isIncompleteIn(clazz)}")
      }
      else if (mix.name.isEmpty && !(sym.owner is Trait))
        // SI-4989 Check if an intermediate class between `clazz` and `sym.owner` redeclares the method as abstract.
        for (intermediateClass <- clazz.info.baseClasses.tail.takeWhile(_ != sym.owner)) {
          val overriding = sym.overridingSymbol(intermediateClass)
          if ((overriding is (Deferred, butNot = AbsOverride)) && !(overriding.owner is Trait))
            ctx.error(
                s"${sym.showLocated} cannot be directly accessed from ${clazz} because ${overriding.owner} redeclares it as abstract",
                sel.pos)

        }
      if (name.isTermName && mix.name.isEmpty &&
          ((clazz is Trait) || clazz != ctx.owner.enclosingClass || !validCurrentClass))
        superAccessorCall(sel)(ctx.withPhase(thisPhase.next))
      else sel
    }

    /** Disallow some super.XX calls targeting Any methods which would
     *  otherwise lead to either a compiler crash or runtime failure.
     */
    private def isDisallowed(sym: Symbol)(implicit ctx: ContextRenamed) =
      sym.isTypeTestOrCast ||
      (sym eq defn.Any_==) ||
      (sym eq defn.Any_!=) ||
      (sym eq defn.Any_##)

    /** Transform select node, adding super and protected accessors as needed */
    def transformSelect(tree: Tree, targs: List[Tree])(implicit ctx: ContextRenamed): Tree = {
      val sel @ Select(qual, name) = tree
      val sym = sel.symbol

      /** If an accesses to protected member of a class comes from a trait,
       *  or would need a protected accessor placed in a trait, we cannot
       *  perform the access to the protected member directly since jvm access
       *  restrictions require the call site to be in an actual subclass and
       *  traits don't count as subclasses in this respect. In this case
       *  we generate a super accessor instead. See SI-2296.
       */
      def needsSuperAccessor =
        ProtectedAccessors.needsAccessorIfNotInSubclass(sym) &&
        AccessProxies.hostForAccessorOf(sym).is(Trait)
      qual match {
        case _: This if needsSuperAccessor =>
          /*
           * A trait which extends a class and accesses a protected member
           *  of that class cannot implement the necessary accessor method
           *  because jvm access restrictions require the call site to be in
           *  an actual subclass and traits don't count as subclasses in this
           *  respect. We generate a super accessor itself, which will be fixed
           *  by the implementing class.  See SI-2296.
           */
          superAccessorCall(sel)
        case Super(_, mix) =>
          transformSuperSelect(sel)
        case _ =>
          sel
      }
    }

    /** Wrap template to template transform `op` with needed initialization and finalization */
    def wrapTemplate(tree: Template)(op: Template => Template)(implicit ctx: ContextRenamed): Template = {
      accDefs(currentClass) = new mutable.ListBuffer[Tree]
      val impl = op(tree)
      val accessors = accDefs.remove(currentClass).get
      if (accessors.isEmpty) impl
      else {
        val (params, rest) = impl.body span {
          case td: TypeDef => !td.isClassDef
          case vd: ValOrDefDef => vd.symbol.flags is ParamAccessor
          case _ => false
        }
        cpy.Template(impl)(body = params ++ accessors ++ rest)
      }
    }

    /** Wrap `DefDef` producing operation `op`, potentially setting `invalidClass` info */
    def wrapDefDef(ddef: DefDef)(op: => DefDef)(implicit ctx: ContextRenamed): DefDef =
      if (isMethodWithExtension(ddef.symbol)) withInvalidCurrentClass(op) else op
}
