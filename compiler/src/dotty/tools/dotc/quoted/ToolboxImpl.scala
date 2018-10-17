package dotty.tools.dotc.quoted

import scala.quoted.{Expr, StagingContext, Type}

/** Default runners for quoted expressions */
object ToolboxImpl {

  def make(settings: scala.quoted.Toolbox.Settings): scala.quoted.Toolbox = new scala.quoted.Toolbox {

    private[this] val driver: QuoteDriver = new QuoteDriver()

    protected def runImpl[T](code: StagingContext => Expr[T]): T = {
      // TODO check for recursion and throw if possible (i.e. run inside a run)
      synchronized(driver.run(code, settings))
    }

  }
}
