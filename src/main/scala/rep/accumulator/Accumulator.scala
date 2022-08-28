package rep.accumulator

import rep.accumulator.Accumulator.Witness
import rep.crypto.Sha256
import java.math.BigInteger
import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks.{break, breakable}

object Accumulator{
  case class Witness(promise:BigInteger,acc_value:BigInteger)
}

class Accumulator(acc_base: BigInteger, last_acc: BigInteger, last_aggregate: BigInteger, hashTool: Sha256) {
  val bitLength = 256
  var acc_value: BigInteger = last_acc
  var acc_aggregate_value: BigInteger = last_aggregate
  var acc_base_value: BigInteger = acc_base


  InitChecked

  private def InitChecked: Unit = {
    //全局累加器的值存放在链上，因为全局累加器的值等于幂模，大小固定，计算量固定。
    //此处检查最后累加器的值是否为空，如果空需要对其进行初始化
    if (acc_base_value == null) {
      this.acc_base_value = PrimeTool.getPrimeOfRandom(bitLength, Rsa2048.getHalfModulus)
    }

    if (this.acc_value == null) {
      this.acc_value = BigInteger.ZERO
    }

    if (this.acc_aggregate_value == null) {
      this.acc_aggregate_value = BigInteger.ONE
    }

    /*//装入累加器成员的乘积，由于累加器的乘积随着元素的增加会越来越大，该乘积放在外面的文件系统，以文件方式存储,该方法要放到外部装入
    val file = new File(last_aggregate_value_file_name)
    if(file.isFile && file.exists()){
      val s1 = scala.io.Source.fromFile(last_aggregate_value_file_name, "UTF-8")
      val l1 = try s1.mkString finally s1.close()
      this.acc_aggregate_value = new BigInteger(l1, 10)
    }*/
  }

  def copy: Accumulator = {
    new Accumulator(this.acc_base_value, this.acc_value, this.acc_aggregate_value, this.hashTool)
  }

  def getAccVaule: BigInteger = {
    this.acc_value
  }

  def getAccBase: BigInteger = {
    this.acc_base_value
  }

  def getAccAgg: BigInteger = {
    this.acc_aggregate_value
  }

  def add(element: Array[Byte]): Accumulator = {
    val prime = PrimeTool.hash2Prime(element, bitLength, hashTool)
    val new_acc_value = if (this.acc_value.compareTo(BigInteger.ZERO) == 0) {
      Rsa2048.exp(this.acc_base_value, prime)
    } else {
      Rsa2048.exp(this.acc_value, prime)
    }
    val agg = Rsa2048.mul(this.acc_aggregate_value, prime)
    new Accumulator(this.acc_base_value, new_acc_value, agg, this.hashTool)
  }

  def addAndWitness(element: Array[Byte]): (Accumulator,Witness) = {
    val n_acc = this.add(element)
    (n_acc,Witness(this.acc_value,n_acc.getAccVaule))
  }

  def addOfBatch(elements: Array[Array[Byte]]): Accumulator = {
    var new_acc = copy
    elements.foreach(e => {
      new_acc = new_acc.add(e)
    })
    new_acc
  }

  /**
   * 建议使用add方法，add和add1方法的执行结果一致
   * */
  def add1(element: Array[Byte]): Accumulator = {
    val prime = PrimeTool.hash2Prime(element, bitLength, hashTool)
    val agg = Rsa2048.mul(this.acc_aggregate_value, prime)
    val new_acc_value = Rsa2048.exp(this.acc_base_value, agg)
    new Accumulator(this.acc_base_value, new_acc_value, agg, this.hashTool)
  }

  def addOfBatch1(elements: Array[Array[Byte]]): Accumulator = {
    var new_acc = copy
    elements.foreach(e => {
      new_acc = new_acc.add1(e)
    })
    new_acc
  }

  def deleteWithWitness(element:Array[Byte],witness: Witness):Accumulator = {
    val prime = PrimeTool.hash2Prime(element, bitLength, hashTool)
    val buf : ArrayBuffer[(BigInteger,BigInteger)] = new ArrayBuffer[(BigInteger,BigInteger)]()
    buf += Tuple2(prime,witness.promise)
    delete(buf.toArray)
  }

  def deleteOfBatchWithWitness(elements:Array[(Array[Byte], Witness)]): Accumulator = {
    val buf : ArrayBuffer[(BigInteger,BigInteger)] = new ArrayBuffer[(BigInteger,BigInteger)]()
    elements.foreach(e=>{
      val prime = PrimeTool.hash2Prime(e._1, bitLength, hashTool)
      buf += Tuple2(prime,e._2.promise)
    })

    delete(buf.toArray)
  }

  private def delete(elements:Array[(BigInteger,BigInteger)]):Accumulator={
    var r : Accumulator = null
    var isExcept = false
    breakable({
      elements.foreach(e=>{
        if(Rsa2048.exp(e._2,e._1).compareTo(this.acc_value) != 0){
          isExcept = true
          break
        }
      })
    })
    if(!isExcept){
      val buf : ArrayBuffer[(BigInteger,BigInteger)] = new ArrayBuffer[(BigInteger,BigInteger)]()
      buf ++= elements
      buf += Tuple2(BigInteger.ONE,this.acc_value)
      val st = divide_and_conquer(buf.toArray)
      if(st != null){
        var agg = Rsa2048.div(this.acc_aggregate_value,st._1)
        r = new Accumulator(this.acc_base_value, st._2, agg, this.hashTool)
      }
    }
    r
  }

  private def divide_and_conquer(elements:Array[(BigInteger,BigInteger)]):(BigInteger,BigInteger)={
    var r : (BigInteger,BigInteger) = null
    if(elements.length > 1){
      var loop = 0
      while(loop < elements.length){
        var p1: BigInteger = null
        var p2: BigInteger = null
        var v1: BigInteger = null
        var v2: BigInteger = null
        if (r == null) {
          p1 = elements(loop)._1
          v1 = elements(loop)._2
          p2 = elements(loop+1)._1
          v2 = elements(loop+1)._2
          loop += 2
        } else {
          p1 = r._1
          v1 = r._2
          p2 = elements(loop)._1
          v2 = elements(loop)._2
          loop += 1
        }
        val st = Util.ShamirTrick(v1,v2,p1,p2)
        if(st == None){
          r = null
          break
        }else{
          r = (Rsa2048.mul(p1,p2),st.get)
        }
      }
    }
    r
  }


  def membershipWitness(member: Array[Byte]): Witness = {
    val prime = PrimeTool.hash2Prime(member, bitLength, hashTool)
    Witness(Rsa2048.exp(this.acc_base_value, Rsa2048.div(this.acc_aggregate_value, prime)),this.acc_value)
  }

  def verifyMembershipWitness(witness:Witness,member:Array[Byte]):Boolean={
    val prime = PrimeTool.hash2Prime(member, bitLength, hashTool)
    if(Rsa2048.exp(witness.promise,prime).compareTo(witness.acc_value) == 0)  true else false
  }

  private def isAggregate(prime: BigInteger): Boolean = {
    val agg = Rsa2048.divideAndRemainder(this.acc_aggregate_value, prime)
    if (agg._2.compareTo(BigInteger.ZERO) == 0) {
      true
    } else {
      false
    }
  }




}
