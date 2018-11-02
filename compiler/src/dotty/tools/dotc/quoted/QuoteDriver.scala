package dotty.tools.dotc.quoted

import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc.Driver
import dotty.tools.dotc.core.Contexts.{ContextRenamed, ContextBase, FreshContext}
import dotty.tools.dotc.tastyreflect.TastyImpl
import dotty.tools.io.{AbstractFile, Directory, PlainDirectory, VirtualDirectory}
import dotty.tools.repl.AbstractFileClassLoader

import scala.quoted.{Expr, Type}
import scala.quoted.Toolbox
import java.net.URLClassLoader

class QuoteDriver extends Driver {
  import tpd._

  private[this] val contextBase: ContextBase = new ContextBase

  def run[T](expr: Expr[T], settings: Toolbox.Settings): T = {
    val outDir: AbstractFile = settings.outDir match {
      case Some(out) =>
        val dir = Directory(out)
        dir.createDirectory()
        new PlainDirectory(Directory(out))
      case None =>
        new VirtualDirectory("<quote compilation output>")
    }

    val (_, ctx0: ContextRenamed) = setup(settings.compilerArgs.toArray :+ "dummy.scala", initCtx.fresh)
    val ctx = setToolboxSettings(ctx0.fresh.setSetting(ctx0.settings.outputDir, outDir), settings)

    val driver = new QuoteCompiler
    driver.newRun(ctx).compileExpr(expr)

    val classLoader = new AbstractFileClassLoader(outDir, this.getClass.getClassLoader)

    val clazz = classLoader.loadClass(driver.outputClassName.toString)
    val method = clazz.getMethod("apply")
    val instance = clazz.getConstructor().newInstance()

    method.invoke(instance).asInstanceOf[T]
  }

  def show(expr: Expr[_], settings: Toolbox.Settings): String = {
    def show(tree: Tree, ctx: ContextRenamed): String = {
      implicit val c: ContextRenamed = ctx
      val tree1 =
        if (ctx.settings.YshowRawQuoteTrees.value) tree
        else (new TreeCleaner).transform(tree)
      new TastyImpl(ctx).showSourceCode.showTree(tree1)
    }
    withTree(expr, show, settings)
  }

  def withTree[T](expr: Expr[_], f: (Tree, ContextRenamed) => T, settings: Toolbox.Settings): T = {
    val ctx = setToolboxSettings(setup(settings.compilerArgs.toArray :+ "dummy.scala", initCtx.fresh)._2.fresh, settings)

    var output: Option[T] = None
    def registerTree(tree: tpd.Tree)(ctx: ContextRenamed): Unit = {
      assert(output.isEmpty)
      output = Some(f(tree, ctx))
    }
    new QuoteDecompiler(registerTree).newRun(ctx).compileExpr(expr)
    output.getOrElse(throw new Exception("Could not extract " + expr))
  }

  def withTypeTree[T](tpe: Type[_], f: (TypTree, ContextRenamed) => T, settings: Toolbox.Settings): T = {
    val (_, ctx: ContextRenamed) = setup(settings.compilerArgs.toArray :+ "dummy.scala", initCtx.fresh)

    var output: Option[T] = None
    def registerTree(tree: tpd.Tree)(ctx: ContextRenamed): Unit = {
      assert(output.isEmpty)
      output = Some(f(tree.asInstanceOf[TypTree], ctx))
    }
    new QuoteDecompiler(registerTree).newRun(ctx).compileType(tpe)
    output.getOrElse(throw new Exception("Could not extract " + tpe))
  }

  override def initCtx: ContextRenamed = {
    val ictx = contextBase.initialCtx
    ictx.settings.classpath.update(QuoteDriver.currentClasspath)(ictx)
    ictx
  }

  private def setToolboxSettings(ctx: FreshContext, settings: Toolbox.Settings): ctx.type = {
    ctx.setSetting(ctx.settings.color, if (settings.color) "always" else "never")
    ctx.setSetting(ctx.settings.YshowRawQuoteTrees, settings.showRawTree)
  }
}

object QuoteDriver {

  def currentClasspath: String = {
    val classpath0 = System.getProperty("java.class.path")
    this.getClass.getClassLoader match {
      case cl: URLClassLoader =>
        // Loads the classes loaded by this class loader
        // When executing `run` or `test` in sbt the classpath is not in the property java.class.path
        val newClasspath = cl.getURLs.map(_.getFile())
        newClasspath.mkString("", java.io.File.pathSeparator, if (classpath0 == "") "" else java.io.File.pathSeparator + classpath0)
      case _ => classpath0
    }
  }

}
