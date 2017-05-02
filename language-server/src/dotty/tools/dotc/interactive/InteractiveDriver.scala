package dotty.tools
package dotc
package interactive

import java.net.URI
import java.nio.file._
import java.util.function._
import java.util.concurrent.CompletableFuture

import org.eclipse.lsp4j
import org.eclipse.lsp4j.jsonrpc.{CancelChecker, CompletableFutures}
import org.eclipse.lsp4j._
import org.eclipse.lsp4j.services._

import java.util.{List => jList}
import java.util.ArrayList

import dotty.tools.dotc._
import dotty.tools.dotc.util._
import dotty.tools.dotc.core.Contexts._
import dotty.tools.dotc.core.SymDenotations._
import dotty.tools.dotc.core._
import dotty.tools.dotc.core.Types._
import dotty.tools.dotc.core.tasty._
import dotty.tools.dotc.{ Main => DottyMain }
import dotty.tools.dotc.interfaces
import dotty.tools.dotc.reporting._
import dotty.tools.dotc.reporting.diagnostic._
import dotty.tools.dotc.classpath.ClassPathEntries

import scala.collection._
import scala.collection.JavaConverters._

import dotty.tools.FatalError
import dotty.tools.io._
import scala.io.Codec
import dotty.tools.dotc.util.SourceFile
import java.io._

import Flags._, Symbols._, Names._, NameOps._
import core.Decorators._

import ast.Trees._

case class SourceTree(source: SourceFile, tree: ast.tpd.Tree)

class ServerDriver(settings: List[String]) extends Driver {
  import ast.tpd._
  import ScalaLanguageServer._

  override protected def newCompiler(implicit ctx: Context): Compiler = ???
  override def sourcesRequired = false

  val openClasses = new mutable.LinkedHashMap[URI, List[TypeName]]

  val openFiles = new mutable.LinkedHashMap[URI, SourceFile]

  def sourcePosition(uri: URI, pos: lsp4j.Position): SourcePosition = {

    val source = openFiles(uri) // might throw exception
    val p = Positions.Position(source.lineToOffset(pos.getLine) + pos.getCharacter)
    new SourcePosition(source, p)
  }

  val myInitCtx = {
    val rootCtx = initCtx.fresh.addMode(Mode.ReadPositions)
    setup(settings.toArray, rootCtx)._2
  }

  implicit def ctx: Context = myCtx

  private[this] var myCtx: Context = myInitCtx
  // private[this] var myCtx: Context = {
  //   val rootCtx = initCtx.fresh.addMode(Mode.ReadPositions)
  //   setup(settings.toArray, rootCtx)._2
  // }

  private[this] def newReporter: Reporter =
    new StoreReporter(null) with UniqueMessagePositions with HideNonSensicalMessages

  val compiler: Compiler = new Compiler

  private def tree(className: TypeName, fromSource: Boolean): Option[SourceTree] = {
    if (className.toString == "scala.annotation.internal.SourceFile")
      None // No SourceFile annotation on SourceFile itself
    else {
      val clsd = ctx.base.staticRef(className)
      clsd match {
        case clsd: ClassDenotation =>
          clsd.info // force denotation
          val tree = clsd.symbol.tree
          if (tree != null) {
            // println("Got tree: " + clsd)
            assert(tree.isInstanceOf[TypeDef])
            assert(tree.symbol.exists, "NO SYMBOL tree: " + tree.show)
            val source = tree.symbol.sourceFile
            assert(source != null, "NO SOURCE tree: " + tree)
            val sourceFile = new SourceFile(source, Codec.UTF8)
            if (!fromSource && openClasses.contains(toUri(sourceFile))) {
              //println("Skipping, already open from source")
              assert(false, clsd) // TODO: remove fromSource parameter
              None
            } else
              Some(SourceTree(sourceFile, tree))
          } else {
            // println("no tree: " + clsd)
            None
          }
        case _ =>
          sys.error(s"class not found: $className")
      }
    }
  }

  lazy val tastyClasses = {
    def classNames(cp: ClassPath, packageName: String): Seq[String] = {
      val ClassPathEntries(pkgs, classReps) = cp.list(packageName)

      classReps
        .filter(classRep => classRep.binary match {
          case None =>
            true
          case Some(binFile) =>
            // FIXME: need a better way to check if classfile has tasty section
            // FIXME: doesn't work with jars
            val tastyFile =
              if (binFile.toString.endsWith("$.class"))
                AbstractFile.getFile(binFile.toString.dropRight("$.class".length) ++ ".class")
              else
                binFile
            tastyFile != null &&
            new classfile.ClassfileParser(tastyFile, null, null)(ctx).hasTasty
        })
        .map(classRep => (packageName ++ (if (packageName != "") "." else "") ++ classRep.name)) ++
        pkgs.flatMap(pkg => classNames(cp, pkg.name))
    }

    classNames(ctx.platform.classPath, "")
  }

  def trees = {
    val sourceClasses = openClasses.values.flatten.toList
    println("openClasses: " + openClasses)
    val otherClasses = tastyClasses.filter(tastyCls =>
      !sourceClasses.exists(sourceCls =>
        tastyCls.toTypeName.stripModuleClassSuffix.toString == sourceCls.stripModuleClassSuffix.toString))

    (sourceClasses.flatMap(c => tree(c, fromSource = true)) ++
      otherClasses.flatMap(c => tree(c.toTypeName, fromSource = false))).toList
  }

  def topLevelClassNames(tree: Tree): List[TypeName] = {
    val names = new mutable.ListBuffer[TypeName]
    object extract extends TreeTraverser {
      override def traverse(tree: Tree)(implicit ctx: Context): Unit = tree match {
        case t: PackageDef =>
          traverseChildren(t)
        case t @ TypeDef(_, tmpl : Template) =>
          names += t.symbol.fullName.asTypeName
        case _ =>
      }
    }
    extract.traverse(tree)
    names.toList
  }

  private def positions(tree: Tree): List[Positions.Position] = {
    val poss = new mutable.ListBuffer[Positions.Position]
    object extract extends TreeTraverser {
      override def traverse(tree: Tree)(implicit ctx: Context): Unit = tree match {
        case t: PackageDef =>
          traverseChildren(t)
        case t @ TypeDef(_, tmpl : Template) =>
          if (t.pos.exists) poss += t.pos
          traverseChildren(tmpl)
        case t: DefDef =>
          if (t.pos.exists) poss += t.pos
        case t: ValDef =>
          if (t.pos.exists) poss += t.pos
        case _ =>
      }
    }
    extract.traverse(tree)
    poss.toList
  }

  def run(uri: URI, sourceCode: String): List[MessageContainer] = {
    try {
      println("run: " + ctx.period)

      val reporter = newReporter
      // val run = compiler.newRun(ctx.fresh.setReporter(reporter))
      val run = compiler.newRun(myInitCtx.fresh.setReporter(reporter))
      myCtx = run.runContext

      val virtualFile = new VirtualFile(uri.toString, Paths.get(uri).toString)
      val writer = new BufferedWriter(new OutputStreamWriter(virtualFile.output, "UTF-8"))
      writer.write(sourceCode)
      writer.close()
      val encoding = Codec.UTF8 // Not sure how to get the encoding from the client
      val sourceFile = new SourceFile(virtualFile, encoding)
      openFiles(uri) = sourceFile

      run.compileSources(List(sourceFile))
      run.printSummary()
      val t = run.units.head.tpdTree
      openClasses(uri) = topLevelClassNames(t)

      reporter.removeBufferedMessages
    }
    catch {
      case ex: FatalError  =>
        ctx.error(ex.getMessage) // signals that we should fail compilation.
        close(uri)
        Nil
    }
    //doCompile(compiler, fileNames)(ctx.fresh.setReporter(reporter))
  }

  def close(uri: URI): Unit = {
    openFiles.remove(uri)
    openClasses.remove(uri)
  }
}
