
/*
 * Copyright (C) 2012-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package scala.compat.java8;

@FunctionalInterface
public interface JFunction1$mcFD$sp extends JFunction1 {
    abstract float apply$mcFD$sp(double v1);

    default Object apply(Object t) { return (Float) apply$mcFD$sp(scala.runtime.BoxesRunTime.unboxToDouble(t)); }
}
