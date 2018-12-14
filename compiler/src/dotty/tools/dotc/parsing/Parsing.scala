package dotty.tools.dotc.parsing

import dotty.tools.dotc.config.Config
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.core.Phases.Phase
import dotty.tools.dotc.parsing.JavaParsers.JavaParser
import dotty.tools.dotc.parsing.Parsers.Parser

class Parsing extends Phase {

  override def phaseName: String = "parsing"

  /** We run TreeChecker only after type checking */
  override def isCheckable: Boolean = false

  override def isRunnable(implicit ctx: Context): Boolean =
    !ctx.settings.fromTasty.value

  override def run(implicit ctx: Context): Unit = monitor("parsing") {
    val unit = ctx.compilationUnit
    unit.untpdTree =
      if (unit.isJava) new JavaParser(unit.source).parse()
      else new Parser(unit.source).parse()

    if (Config.checkPositions)
      unit.untpdTree.checkPos(nonOverlapping = !unit.isJava && !ctx.reporter.hasErrors)
  }
}
