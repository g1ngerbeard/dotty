import quoted._

object Test {
  def main(args: Array[String]): Unit = {
    val tb = Toolbox.make
    println(tb.show {
      '{
        type T = String
        val x = "foo"
        ~{
          val y = '(x)
          '{ val z: T = ~y }
        }
        x
      }
    })
  }
}
