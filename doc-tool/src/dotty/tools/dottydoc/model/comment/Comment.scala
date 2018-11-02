package dotty.tools
package dottydoc
package model
package comment

import dotty.tools.dottydoc.util.syntax._
import dotty.tools.dotc.core.Contexts.ContextRenamed
import dotty.tools.dotc.util.Positions._
import com.vladsch.flexmark.ast.{ Node => MarkdownNode }
import HtmlParsers._
import util.MemberLookup

case class Comment (
  body:                    String,
  short:                   String,
  authors:                 List[String],
  see:                     List[String],
  result:                  Option[String],
  throws:                  Map[String, String],
  valueParams:             Map[String, String],
  typeParams:              Map[String, String],
  version:                 Option[String],
  since:                   Option[String],
  todo:                    List[String],
  deprecated:              Option[String],
  note:                    List[String],
  example:                 List[String],
  constructor:             Option[String],
  group:                   Option[String],
  groupDesc:               Map[String, String],
  groupNames:              Map[String, String],
  groupPrio:               Map[String, String],
  /** List of conversions to hide - containing e.g: `scala.Predef.FloatArrayOps` */
  hideImplicitConversions: List[String]
)

private[comment] case class ParsedComment (
  body:                    String,
  authors:                 List[String],
  see:                     List[String],
  result:                  List[String],
  throws:                  Map[String, String],
  valueParams:             Map[String, String],
  typeParams:              Map[String, String],
  version:                 List[String],
  since:                   List[String],
  todo:                    List[String],
  deprecated:              List[String],
  note:                    List[String],
  example:                 List[String],
  constructor:             List[String],
  group:                   List[String],
  groupDesc:               Map[String, String],
  groupNames:              Map[String, String],
  groupPrio:               Map[String, String],
  hideImplicitConversions: List[String],
  shortDescription:        List[String]
)

trait MarkupConversion[T] extends MemberLookup {
  def ent: Entity
  def pos: Position
  def parsed: ParsedComment

  protected def linkedExceptions(m: Map[String, String])(implicit ctx: ContextRenamed): Map[String, String]
  protected def stringToMarkup(str: String)(implicit ctx: ContextRenamed): T
  protected def markupToHtml(t: T)(implicit ctx: ContextRenamed): String
  protected def stringToShortHtml(str: String)(implicit ctx: ContextRenamed): String
  protected def filterEmpty(xs: List[String])(implicit ctx: ContextRenamed): List[T]
  protected def filterEmpty(xs: Map[String, String])(implicit ctx: ContextRenamed): Map[String, T]

  private def single(annot: String, xs: List[String], filter: Boolean = true)(implicit ctx: ContextRenamed): Option[T] =
    (if (filter) filterEmpty(xs) else xs.map(stringToMarkup)) match {
      case x :: xs =>
        if (xs.nonEmpty) ctx.docbase.warn(
          s"Only allowed to have a single annotation for $annot",
          ent.symbol.sourcePosition(pos)
        )
        Some(x)
      case _ => None
    }

  final def comment(implicit ctx: ContextRenamed): Comment = Comment(
    body                    = markupToHtml(stringToMarkup(parsed.body)),
    short                   = stringToShortHtml(parsed.body),
    authors                 = filterEmpty(parsed.authors).map(markupToHtml),
    see                     = filterEmpty(parsed.see).map(markupToHtml),
    result                  = single("@result", parsed.result).map(markupToHtml),
    throws                  = linkedExceptions(parsed.throws),
    valueParams             = filterEmpty(parsed.valueParams).mapValues(markupToHtml),
    typeParams              = filterEmpty(parsed.typeParams).mapValues(markupToHtml),
    version                 = single("@version", parsed.version).map(markupToHtml),
    since                   = single("@since", parsed.since).map(markupToHtml),
    todo                    = filterEmpty(parsed.todo).map(markupToHtml),
    deprecated              = single("@deprecated", parsed.deprecated, filter = false).map(markupToHtml),
    note                    = filterEmpty(parsed.note).map(markupToHtml),
    example                 = filterEmpty(parsed.example).map(markupToHtml),
    constructor             = single("@constructor", parsed.constructor).map(markupToHtml),
    group                   = single("@group", parsed.group).map(markupToHtml),
    groupDesc               = filterEmpty(parsed.groupDesc).mapValues(markupToHtml),
    groupNames              = filterEmpty(parsed.groupNames).mapValues(markupToHtml),
    groupPrio               = filterEmpty(parsed.groupPrio).mapValues(markupToHtml),
    hideImplicitConversions = filterEmpty(parsed.hideImplicitConversions).map(markupToHtml)
  )
}

case class MarkdownComment(ent: Entity, parsed: ParsedComment, pos: Position)
extends MarkupConversion[MarkdownNode] {

  def stringToMarkup(str: String)(implicit ctx: ContextRenamed) =
    str.toMarkdown(ent)

  def stringToShortHtml(str: String)(implicit ctx: ContextRenamed) =
    str.toMarkdown(ent).shortenAndShow

  def markupToHtml(md: MarkdownNode)(implicit ctx: ContextRenamed) =
    md.show

  def linkedExceptions(m: Map[String, String])(implicit ctx: ContextRenamed) = {
    val inlineToHtml = InlineToHtml(ent)
    m.map { case (targetStr, body) =>
      val link = makeEntityLink(ent, ctx.docbase.packages, Monospace(Text(targetStr)), targetStr)
      (targetStr, inlineToHtml(link))
    }
  }

  def filterEmpty(xs: List[String])(implicit ctx: ContextRenamed) =
    xs.map(_.trim)
      .filterNot(_.isEmpty)
      .map(stringToMarkup)

  def filterEmpty(xs: Map[String, String])(implicit ctx: ContextRenamed) =
    xs.mapValues(_.trim)
      .filterNot { case (_, v) => v.isEmpty }
      .mapValues(stringToMarkup)
}

case class WikiComment(ent: Entity, parsed: ParsedComment, pos: Position)
extends MarkupConversion[Body] {

  def filterEmpty(xs: Map[String,String])(implicit ctx: ContextRenamed) =
    xs.mapValues(_.toWiki(ent, ctx.docbase.packages, pos))
      .filterNot { case (_, v) => v.blocks.isEmpty }

  def filterEmpty(xs: List[String])(implicit ctx: ContextRenamed) =
    xs.map(_.toWiki(ent, ctx.docbase.packages, pos))

  def markupToHtml(t: Body)(implicit ctx: ContextRenamed) =
    t.show(ent)

  def stringToMarkup(str: String)(implicit ctx: ContextRenamed) =
    str.toWiki(ent, ctx.docbase.packages, pos)

  def stringToShortHtml(str: String)(implicit ctx: ContextRenamed) = {
    val parsed = stringToMarkup(str)
    parsed.summary.getOrElse(parsed).show(ent)
  }

  def linkedExceptions(m: Map[String, String])(implicit ctx: ContextRenamed) = {
    m.mapValues(_.toWiki(ent, ctx.docbase.packages, pos)).map { case (targetStr, body) =>
      val link = lookup(Some(ent), ctx.docbase.packages, targetStr)
      val newBody = body match {
        case Body(List(Paragraph(Chain(content)))) =>
          val descr = Text(" ") +: content
          val link = makeEntityLink(ent, ctx.docbase.packages, Monospace(Text(targetStr)), targetStr)
          Body(List(Paragraph(Chain(link +: descr))))
        case _ => body
      }

      (targetStr, newBody.show(ent))
    }
  }
}
