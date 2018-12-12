object IntepretedMain {
  def main(args: Array[String]): Unit = {
    val x1 = 42
    println(x1)
    println()

    lazy val x2 = println("Hello")
    x2
    x2
    println()

    def x3 = 42
    println(x3)
    println()

    var x4: Int = 42
    x4 = 43
    println(x4)
    println()

    if(x1 == 42)
      println("if")
    else
      println("else")
    println()

    var x5 = 5
    while(x5 > 0){
      println(x5)
      x5 = x5 - 1
    }
    println()

    def meth() = 42
    println(meth())
    println()

    def methP(i: Int) = i
    println(methP(55))

    println(Precompiled)
    println(Precompiled.staticMeth)
    println(Precompiled.staticVal)
    println(Precompiled.staticMeth1())
    // println(Precompiled.staticMeth2(58))
  }
}
