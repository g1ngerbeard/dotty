package dotty.tools.dotc
package transform
package init

import core._
import MegaPhase._
import Contexts.Context
import StdNames._
import Names._
import Phases._
import ast._
import Trees._
import Flags._
import SymUtils._
import Symbols._
import Denotations._
import SymDenotations._
import Types._
import Decorators._
import DenotTransformers._
import util.Positions._
import config.Printers.init.{ println => debug }
import Constants.Constant
import collection.mutable


trait Indexer { self: Analyzer =>
  import tpd._

  def methodValue(ddef: DefDef)(implicit setting: Setting): FunctionValue =
    new FunctionValue { fv =>
      def apply(values: Int => Value, argPos: Int => Position)(implicit setting2: Setting): Res = {
        // TODO: implicit ambiguities
        implicit val ctx: Context = setting2.ctx
        if (isChecking(ddef.symbol)) {
          // TODO: check if fixed point has reached. But the domain is infinite, thus non-terminating.
          debug(s"recursive call of ${ddef.symbol} found")
          Res()
        }
        else {
          val env2 = setting.env.fresh(setting2.heap)
          val setting3 = setting2.withCtx(setting2.ctx.withOwner(ddef.symbol)).withEnv(env2)

          ddef.vparamss.flatten.zipWithIndex.foreach { case (param: ValDef, index) =>
            env2.add(param.symbol, value = values(index))
          }
          val res = checking(ddef.symbol) { self.apply(ddef.rhs)(setting3) }
          if (res.hasErrors) res.effects = Vector(Call(ddef.symbol, res.effects, setting2.pos))
          res
        }
      }

      def widen(implicit setting2: Setting) = {
        // TODO: implicit ambiguities
        implicit val ctx: Context = setting2.ctx
        val env = setting2.heap(setting.env.id).asEnv
        val setting3 = setting2.withCtx(setting2.ctx.withOwner(ddef.symbol)).withEnv(env)
        setting3.widenFor(fv) { widenTree(ddef)(setting3) }
      }

      override def show(implicit setting: ShowSetting): String = ddef.symbol(setting.ctx).show(setting.ctx)
    }

  def classValue(cdef: TypeDef)(implicit setting: Setting): ClassValue =
    new ClassValue { cv =>
      def init(constr: Symbol, values: List[Value], argPos: List[Position], obj: ObjectValue)(implicit setting2: Setting): Res = {
        // TODO: implicit ambiguities
        implicit val ctx: Context = setting2.ctx
        if (isChecking(cdef.symbol)) {
          // TODO: check if fixed point has reached. But the domain is infinite, thus non-terminating.
          debug(s"recursive instantiation of ${cdef.symbol} found")
          Res()
        }
        else {
          val tmpl = cdef.rhs.asInstanceOf[Template]
          val env2 = setting.env.fresh(setting2.heap)
          val setting3 = setting2.withCtx(setting2.ctx.withOwner(cdef.symbol)).withEnv(env2)
          self.init(constr, tmpl, values, argPos, obj)(setting3)
        }
      }

      def widen(implicit setting2: Setting) = {
        // TODO: implicit ambiguities
        implicit val ctx: Context = setting2.ctx
        val env = setting2.heap(setting.env.id).asEnv
        val setting3 = setting2.withCtx(setting2.ctx.withOwner(cdef.symbol)).withEnv(env)
        setting3.widenFor(cv) { widenTree(cdef)(setting3) }
      }

      override def show(implicit setting: ShowSetting): String = cdef.symbol(setting.ctx).show(setting.ctx)
    }

  def widenTree(tree: Tree)(implicit setting: Setting): OpaqueValue = {
    val captured = Capture.analyze(tree)
    indentedDebug(s"captured in ${tree.symbol}: " + captured.keys.map(_.show).mkString(", "))

    val value = WarmValue(captured.keys.toSet, unknownDeps = false).widen
    if (!value.isHot) indentedDebug(s"not hot in ${tree.symbol}: " + value.show(setting.showSetting))
    value
  }

  def lazyValue(vdef: ValDef)(implicit setting: Setting): LazyValue =
    new LazyValue { lz =>
      def apply(values: Int => Value, argPos: Int => Position)(implicit setting2: Setting): Res = {
        // TODO: implicit ambiguities
        implicit val ctx: Context = setting2.ctx
        if (isChecking(vdef.symbol)) {
          // TODO: check if fixed point has reached. But the domain is infinite, thus non-terminating.
          debug(s"recursive call of ${vdef.symbol} found")
          Res()
        }
        else {
          val env2 = setting2.heap(setting.env.id).asEnv
          val setting3: Setting = setting2.withCtx(setting2.ctx.withOwner(vdef.symbol)).withEnv(env2)
          val res = checking(vdef.symbol) { self.apply(vdef.rhs)(setting3) }
          if (res.hasErrors) res.effects = Vector(Force(vdef.symbol, res.effects, setting2.pos))
          res
        }
      }

      def widen(implicit setting2: Setting) = {
        // TODO: implicit ambiguities
        implicit val ctx: Context = setting2.ctx
        val env = setting2.heap(setting.env.id).asEnv
        val setting3 = setting2.withCtx(setting2.ctx.withOwner(vdef.symbol)).withEnv(env)
        setting3.widenFor(lz) { widenTree(vdef)(setting3) }
      }

      override def show(implicit setting: ShowSetting): String = vdef.symbol(setting.ctx).show(setting.ctx)
    }

  /** Index local definitions */
  def indexStats(stats: List[Tree])(implicit setting: Setting): Unit = stats.foreach {
    case ddef: DefDef if !ddef.symbol.isConstructor =>  // TODO: handle secondary constructor
      setting.env.add(ddef.symbol, methodValue(ddef))
    case vdef: ValDef if vdef.symbol.is(Lazy)  =>
      setting.env.add(vdef.symbol, lazyValue(vdef))
    case vdef: ValDef =>
      setting.env.add(vdef.symbol, NoValue)
    case tdef: TypeDef if tdef.isClassDef  =>
      setting.env.add(tdef.symbol.asClass, classValue(tdef))
    case _ =>
  }

  /** Index member definitions
   *
   *  trick: use `slice` for name resolution, but `env` for method execution
   */
  def indexMembers(stats: List[Tree], slice: SliceRep)(implicit setting: Setting): Unit = stats.foreach {
    case ddef: DefDef =>
      slice.add(ddef.symbol, methodValue(ddef)(setting.withEnv(slice.innerEnv)))
    case vdef: ValDef if vdef.symbol.is(Lazy)  =>
      slice.add(vdef.symbol, lazyValue(vdef)(setting.withEnv(slice.innerEnv)))
    case vdef: ValDef =>
      val value = if (vdef.symbol.isInit || vdef.symbol.is(Deferred)) HotValue else NoValue
      slice.add(vdef.symbol, value)
    case tdef: TypeDef if tdef.isClassDef  =>
      slice.add(tdef.symbol.asClass, classValue(tdef)(setting.withEnv(slice.innerEnv)))
    case _ =>
  }

  def init(constr: Symbol, tmpl: Template, values: List[Value], argPos: List[Position], obj: ObjectValue)(implicit setting: Setting): Res = {
    val cls = constr.owner.asClass

    if (isChecking(cls)) {
      debug(s"recursive creation of $cls found")
      Res()
    }
    else checking(cls) {
      val slice = setting.env.newSlice(cls)
      obj.add(cls, new SliceValue(slice.id))

      // The outer of parents are set (but not recursively)
      // before any super-calls if they are known.
      // This is not specified in the Scala specification.
      // Calling methods of an unrelated trait during initialization
      // is dangerous, thus should be discouraged. Therefore, the analyzer
      // doesn't follow closely the semantics here.

      // first index current class
      indexMembers(tmpl.body, slice)

      // propagate constructor arguments
      tmpl.constr.vparamss.flatten.zipWithIndex.foreach { case (param: ValDef, index) =>
        val sym = cls.info.member(param.name).suchThat(x => !x.is(Method)).symbol
        if (sym.exists) slice.add(sym, values(index))
        slice.innerEnv.add(param.symbol, values(index))
      }

      // setup this
      slice.innerEnv.add(cls, obj)

      // call parent constructor
      val setting2 = setting.withCtx(setting.ctx.withOwner(cls.owner)).withEnv(slice.innerEnv).withPos(cls.pos)
      val res = checkParents(cls, tmpl.parents, obj)(setting2)
      if (res.hasErrors) return res

      // check current class body
      res ++= checkStats(tmpl.body)(setting2).effects
      res
    }
  }
}
