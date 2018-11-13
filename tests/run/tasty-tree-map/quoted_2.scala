
import Macros._

object Test {

  def main(args: Array[String]): Unit = {
    println(identityMaped(32))
    println(identityMaped(32 + 1))
    println(identityMaped({ val i = 34; i }))
    println(identityMaped({ var i = 34; i += 1; i }))
    println(identityMaped({ var i = 0; while (i < 36) i += 1; i }))
    println(identityMaped({ var i = 0; do i += 1 while (i < 37); i }))
    println(identityMaped(try 38 finally ()))
    println(identityMaped(try 39 catch { case _: Error => }))
    println(identityMaped(new java.lang.Integer(40)))
    println(identityMaped(if (true: Boolean) 41 else -1))
    println(identityMaped(true match { case _ => 42 } ))
    println(identityMaped({ def f = 43; f }))
    println(identityMaped({ def f() = 44; f() }))
    println(identityMaped({ def f[T] = 45; f[Int] }))
    println(identityMaped({ def f: Int = return 46; f }))
    println(identityMaped({ def f(a: Int): Int = a; f(a = 47) }))
//    println(identityMaped({ def f(a: Int*): Int = a.sum; f(47, 1) }))
    println(identityMaped(((a: Int) => a)(49)))
    println(identityMaped({ type A = Int; 50: A }))
    println(identityMaped({ import scala.{Int => I}; 51: I }))
    println(identityMaped(52 match { case x => x }))
    println(identityMaped(53 match { case x: Int => x }))
    println(identityMaped((0: Any) match { case _: Int | _: Double => 54 }))
    println(identityMaped(0 match { case I55(x) => x }))
    // TODO add more tests
  }

  object I55 {
    def unapply(arg: Any): Some[Int] = Some(55)
  }
}
