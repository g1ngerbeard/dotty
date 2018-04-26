package dotty.tools
package dotc
package core

import util.common._
import Types._
import Symbols._
import Flags._
import Names._
import Contexts._
import SymDenotations._
import Denotations._
import Decorators._
import reporting.diagnostic.Message
import reporting.diagnostic.messages._
import ast.untpd
import config.Printers.cyclicErrors

class TypeError(msg: String) extends Exception(msg) {
  def this() = this("")
  def toMessage(implicit ctx: Context): Message = getMessage
}

class MalformedType(pre: Type, denot: Denotation, absMembers: Set[Name]) extends TypeError {
  override def toMessage(implicit ctx: Context): Message =
    i"malformed type: $pre is not a legal prefix for $denot because it contains abstract type member${if (absMembers.size == 1) "" else "s"} ${absMembers.mkString(", ")}"
}

class MissingType(pre: Type, name: Name) extends TypeError {
  private def otherReason(pre: Type)(implicit ctx: Context): String = pre match {
    case pre: ThisType if pre.cls.givenSelfType.exists =>
      i"\nor the self type of $pre might not contain all transitive dependencies"
    case _ => ""
  }

  override def toMessage(implicit ctx: Context): Message = {
    if (ctx.debug) printStackTrace()
    i"""cannot resolve reference to type $pre.$name
       |the classfile defining the type might be missing from the classpath${otherReason(pre)}"""
  }
}

class RecursionOverflow(val op: String, details: => String, previous: Throwable, val weight: Int) extends TypeError {

  def explanation = s"$op $details"

  private def recursions: List[RecursionOverflow] = {
    val nested = previous match {
      case previous: RecursionOverflow => previous.recursions
      case _ => Nil
    }
    this :: nested
  }

  def opsString(rs: List[RecursionOverflow])(implicit ctx: Context): String = {
    val maxShown = 20
    if (rs.lengthCompare(maxShown) > 0)
      i"""${opsString(rs.take(maxShown / 2))}
         |  ...
         |${opsString(rs.takeRight(maxShown / 2))}"""
    else
      (rs.map(_.explanation): List[String]).mkString("\n  ", "\n|  ", "")
  }

  override def toMessage(implicit ctx: Context): Message = {
    val mostCommon = recursions.groupBy(_.op).toList.maxBy(_._2.map(_.weight).sum)._2.reverse
    s"""Recursion limit exceeded.
       |Maybe there is an illegal cyclic reference?
       |A recurring operation is (inner to outer):
       |${opsString(mostCommon)}""".stripMargin
  }

  override def fillInStackTrace(): Throwable = this
  override def getStackTrace() = previous.getStackTrace()
}

object handleRecursive {
  def apply(op: String, details: => String, exc: Throwable, weight: Int = 1): Nothing = exc match {
    case _: RecursionOverflow =>
      throw new RecursionOverflow(op, details, exc, weight)
    case _ =>
      var e = exc
      while (e != null && !e.isInstanceOf[StackOverflowError]) e = e.getCause
      if (e != null) throw new RecursionOverflow(op, details, e, weight)
      else throw exc
  }
}

class CyclicReference private (val denot: SymDenotation) extends TypeError {

  override def toMessage(implicit ctx: Context) = {

    def errorMsg(cx: Context): Message =
      if (cx.mode is Mode.InferringReturnType) {
        cx.tree match {
          case tree: untpd.DefDef if !tree.tpt.typeOpt.exists =>
            OverloadedOrRecursiveMethodNeedsResultType(tree.name)
          case tree: untpd.ValDef if !tree.tpt.typeOpt.exists =>
            RecursiveValueNeedsResultType(tree.name)
          case _ =>
            errorMsg(cx.outer)
        }
      }
      else CyclicReferenceInvolving(denot)

    val cycleSym = denot.symbol
    if (cycleSym.is(Implicit, butNot = Method) && cycleSym.owner.isTerm)
      CyclicReferenceInvolvingImplicit(cycleSym)
    else
      errorMsg(ctx)
  }
}

object CyclicReference {
  def apply(denot: SymDenotation)(implicit ctx: Context): CyclicReference = {
    val ex = new CyclicReference(denot)
    if (!(ctx.mode is Mode.CheckCyclic)) {
      cyclicErrors.println(ex.getMessage)
      for (elem <- ex.getStackTrace take 200)
        cyclicErrors.println(elem.toString)
    }
    ex
  }
}

class MergeError(msg: String, val tp1: Type, val tp2: Type) extends TypeError(msg)