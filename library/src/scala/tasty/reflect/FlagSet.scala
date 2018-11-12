package scala.tasty.reflect

trait FlagSet {

  /** Is this symbol `protected` */
  def isProtected: Boolean

  /** Is this symbol `abstract` */
  def isAbstract: Boolean

  /** Is this symbol `final` */
  def isFinal: Boolean

  /** Is this symbol `sealed` */
  def isSealed: Boolean

  /** Is this symbol `case`. */
  def isCase: Boolean

  /** Is this symbol `implicit`. */
  def isImplicit: Boolean

  /** Is this symbol `erased`. */
  def isErased: Boolean

  /** Is this symbol `lazy`. */
  def isLazy: Boolean

  /** Is this symbol `override`. */
  def isOverride: Boolean

  /** Is this symbol `inline`. */
  def isInline: Boolean

  /** Is this symbol markes as a macro. An inline method containing toplevel splices. */
  def isMacro: Boolean

  /** Is this symbol marked as static. Mapped to static Java member. */
  def isStatic: Boolean

  /** Is this symbol an object or its class (used for a ValDef or a ClassDef extends Modifier respectively) */
  def isObject: Boolean

  /** Is this symbol a trait. */
  def isTrait: Boolean

  /** Is this symbol local. Used in conjunction with Private/private[Type] to mean private[this] extends Modifier proctected[this]. */
  def isLocal: Boolean

  /** Was this symbol generated by Scala compiler. */
  def isSynthetic: Boolean

  /** Is this symbol to be tagged Java Synthetic. */
  def isArtifact: Boolean

  /** Is this symbol a `var` (when used on a ValDef). */
  def isMutable: Boolean

  /** Is this symbol a getter or a setter. */
  def isFieldAccessor: Boolean

  /** Is this symbol a getter for case class parameter. */
  def isCaseAcessor: Boolean

  /** Is this symbol a type parameter marked as covariant `+`. */
  def isCovariant: Boolean

  /** Is this symbol a type parameter marked as contravariant `-`. */
  def isContravariant: Boolean

  /** Was this symbol imported from Scala2.x. */
  def isScala2X: Boolean

  /** Is this symbol a method with default parameters. */
  def isDefaultParameterized: Boolean

  /** Is this symbol member that is assumed to be stable. */
  def isStable: Boolean

  /** Is this symbol a parameter. */
  def isParam: Boolean

  /** Is this symbol a parameter accessor. */
  def isParamAccessor: Boolean
}
