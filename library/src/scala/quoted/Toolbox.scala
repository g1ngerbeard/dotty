package scala.quoted

trait Toolbox {
  def run[T](code: Staged[T]): T = runImpl(code)
  protected def runImpl[T](code: StagingContext => Expr[T]): T // For Scala2 compat in ToolboxImpl

  def show[T](code: Staged[T]): String = runImpl(ctx => { implicit val c: StagingContext = ctx; code(ctx).show.toExpr })
}

object Toolbox {

  def make(implicit settings: Settings): Toolbox = {
    val cl = getClass.getClassLoader
    try {
      val toolboxImplCls = cl.loadClass("dotty.tools.dotc.quoted.ToolboxImpl")
      val makeMeth = toolboxImplCls.getMethod("make", classOf[Settings])
      makeMeth.invoke(null, settings).asInstanceOf[Toolbox]
    }
    catch {
      case ex: ClassNotFoundException =>
        throw new ToolboxNotFoundException(
          s"""Could not load the Toolbox class `${ex.getMessage}` from the JVM classpath. Make sure that the compiler is on the JVM classpath.""",
          ex
        )
    }
  }

  /** Setting of the Toolbox instance. */
  class Settings private (val outDir: Option[String], val showRawTree: Boolean, val compilerArgs: List[String], val color: Boolean)

  object Settings {

    implicit def default: Settings = make()

    /** Make toolbox settings
     *  @param outDir Output directory for the compiled quote. If set to None the output will be in memory
     *  @param color Print output with colors
     *  @param showRawTree Do not remove quote tree artifacts
     *  @param compilerArgs Compiler arguments. Use only if you know what you are doing.
     */
    def make( // TODO avoid using default parameters (for binary compat)
      color: Boolean = false,
      showRawTree: Boolean = false,
      outDir: Option[String] = None,
      compilerArgs: List[String] = Nil
    ): Settings =
      new Settings(outDir, showRawTree, compilerArgs, color)
  }

  class ToolboxNotFoundException(msg: String, cause: ClassNotFoundException) extends Exception(msg, cause)
}
