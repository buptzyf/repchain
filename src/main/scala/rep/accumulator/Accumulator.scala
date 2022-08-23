package rep.accumulator

import java.io.File
import java.math.BigInteger

class Accumulator(last_acc_value:BigInt,last_aggregate_value_file_name:String) {
  var acc_value : BigInt = last_acc_value
  var acc_aggregate_value : BigInt = BigInteger.ONE
  InitChecked

  private def InitChecked:Unit={
    //全局累加器的值存放在链上，因为全局累加器的值等于幂模，大小固定，计算量固定。
    //此处检查最后累加器的值是否为空，如果空需要对其进行初始化
    if(this.acc_value == null) {
      //val rnd = new EnhancedRandom
      //this.acc_value = rnd.nextBigInteger(Rsa2048.getRSAModule)
    }
    //装入累加器成员的乘积，由于累加器的乘积随着元素的增加会越来越大，该乘积放在外面的文件系统，以文件方式存储
    val file = new File(last_aggregate_value_file_name)
    if(file.isFile && file.exists()){
      val s1 = scala.io.Source.fromFile(last_aggregate_value_file_name, "UTF-8")
      val l1 = try s1.mkString finally s1.close()
      this.acc_aggregate_value = BigInt.apply(l1, 10)
    }
  }

}
