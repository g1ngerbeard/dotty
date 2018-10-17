import quoted._

object Test {
  def main(args: Array[String]): Unit = {
    val tb = Toolbox.make
    println(tb.show {
      '{
        val a = '(4)
        ~('{
          '(~a)
        })
      }
    })
  }
}
