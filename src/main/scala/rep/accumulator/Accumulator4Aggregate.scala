package rep.accumulator


import java.math.BigInteger
import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks.{break, breakable}
import rep.crypto.Sha256
import rep.accumulator.Accumulator4Aggregate.{AccumulatorWithMembershipProof, MembershipProof, NonWitness, NonmembershipProof, Witness, bitLength}
import rep.accumulator.verkle.util.proofStruct

object Accumulator4Aggregate{
  val bitLength = 256
  case class NonWitness(d_coefficient:BigInteger,v_coefficient:BigInteger,gv_inv:BigInteger)
  case class Witness(promise:BigInteger,agg:BigInteger)
  case class MembershipProof(proof:BigInteger,witness:Witness)
  case class AccumulatorWithMembershipProof(acc:Accumulator4Aggregate,proof: MembershipProof)
  case class NonmembershipProof(nonWitness:NonWitness,proof_poe:BigInteger,proof_poke2:proofStruct)
}

class Accumulator4Aggregate(acc_base: BigInteger, last_acc: BigInteger, last_aggregate: BigInteger, hashTool: Sha256) {
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
  }

  def copy: Accumulator4Aggregate = {
    new Accumulator4Aggregate(this.acc_base_value, this.acc_value, this.acc_aggregate_value, this.hashTool)
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

  /////////////////////累加器的累加操作（累加，累加之后输出证明）、验证证明----开始/////////////////////////
  def add(element: Array[Byte]): Accumulator4Aggregate = {
    val prime = PrimeTool.hash2Prime(element, bitLength, hashTool)
    val new_acc_value = if (this.acc_value.compareTo(BigInteger.ZERO) == 0) {
      Rsa2048.exp(this.acc_base_value, prime)
    } else {
      Rsa2048.exp(this.acc_value, prime)
    }
    val agg = Rsa2048.mul(this.acc_aggregate_value, prime)
    new Accumulator4Aggregate(this.acc_base_value, new_acc_value, agg, this.hashTool)
  }

  def add(elements: Array[Array[Byte]]): Accumulator4Aggregate = {
    var acc : Accumulator4Aggregate = null
    elements.foreach(e=>{
      if(acc == null){
        acc = add(e)
      }else{
        acc = acc.add(e)
      }
    })
    acc
  }

  def membershipWitness(member: Array[Byte]): Witness = {
    val prime = PrimeTool.hash2Prime(member, bitLength, hashTool)
    val a = Rsa2048.div(Rsa2048.mod(acc_value),Rsa2048.exp(this.acc_base_value,prime))
    val agg = Rsa2048.div(this.acc_aggregate_value,prime)
    Witness(a, agg)
  }

  def addAndProof(element: Array[Byte]):AccumulatorWithMembershipProof = {
    val prime = PrimeTool.hash2Prime(element, bitLength, hashTool)
    val new_acc_value = if (this.acc_value.compareTo(BigInteger.ZERO) == 0) {
      Rsa2048.exp(this.acc_base_value, prime)
    } else {
      Rsa2048.exp(this.acc_value, prime)
    }
    val agg = Rsa2048.mul(this.acc_aggregate_value, prime)
    val acc = new Accumulator4Aggregate(this.acc_base_value, new_acc_value, agg, this.hashTool)
    val proof = Poe.prove(this.acc_value,prime,acc.getAccVaule,bitLength,hashTool)

    AccumulatorWithMembershipProof(acc, MembershipProof(proof,Witness(this.acc_value,this.acc_aggregate_value)))
  }

  def verifyMembershipProof(member: Array[Byte],proof:MembershipProof): Boolean = {
    val prime = PrimeTool.hash2Prime(member, bitLength, hashTool)
    Poe.verify(proof.witness.promise,prime,this.acc_value,proof.proof,bitLength,hashTool)
  }
  /////////////////////累加器的累加操作（累加，累加之后输出证明）、验证证明----结束/////////////////////////


  /////////////////////累加器的带证明的删除操作（带见证的删除、删除之后输出证明）----开始/////////////////////////
  def deleteWithWitness(element: Array[Byte], witness: Witness): Accumulator4Aggregate = {
    val prime = PrimeTool.hash2Prime(element, bitLength, hashTool)
    val buf: ArrayBuffer[(BigInteger, BigInteger)] = new ArrayBuffer[(BigInteger, BigInteger)]()
    buf += Tuple2(prime, witness.promise)
    delete(buf.toArray)
  }

  def deleteWithWitnessToProof(element: Array[Byte], witness: Witness): AccumulatorWithMembershipProof = {
    val prime = PrimeTool.hash2Prime(element, bitLength, hashTool)
    val buf: ArrayBuffer[(BigInteger, BigInteger)] = new ArrayBuffer[(BigInteger, BigInteger)]()
    buf += Tuple2(prime, witness.promise)
    val acc = delete(buf.toArray)
    val proof = Poe.prove(acc.getAccVaule,prime,this.acc_value,bitLength,hashTool)
    AccumulatorWithMembershipProof(acc, MembershipProof(proof,Witness(acc.getAccVaule,acc.getAccAgg)))
  }

  private def delete(elements: Array[(BigInteger, BigInteger)]): Accumulator4Aggregate = {
    var r: Accumulator4Aggregate = null
    var isExcept = false
    breakable({
      elements.foreach(e => {
        if (Rsa2048.exp(e._2, e._1).compareTo(this.acc_value) != 0) {
          isExcept = true
          break
        }
      })
    })
    if (!isExcept) {
      val buf: ArrayBuffer[(BigInteger, BigInteger)] = new ArrayBuffer[(BigInteger, BigInteger)]()
      buf ++= elements
      buf += Tuple2(BigInteger.ONE, this.acc_value)
      val st = divide_and_conquer(buf.toArray)
      if (st != null) {
        var agg = Rsa2048.div(this.acc_aggregate_value, st._1)
        r = new Accumulator4Aggregate(this.acc_base_value, st._2, agg, this.hashTool)
      }
    }
    r
  }

  private def divide_and_conquer(elements: Array[(BigInteger, BigInteger)]): (BigInteger, BigInteger) = {
    var r: (BigInteger, BigInteger) = null
    if (elements.length > 1) {
      var loop = 0
      while (loop < elements.length) {
        var p1: BigInteger = null
        var p2: BigInteger = null
        var v1: BigInteger = null
        var v2: BigInteger = null
        if (r == null) {
          p1 = elements(loop)._1
          v1 = elements(loop)._2
          p2 = elements(loop + 1)._1
          v2 = elements(loop + 1)._2
          loop += 2
        } else {
          p1 = r._1
          v1 = r._2
          p2 = elements(loop)._1
          v2 = elements(loop)._2
          loop += 1
        }
        val st = Util.ShamirTrick(v1, v2, p1, p2)
        if (st == None) {
          r = null
          break
        } else {
          r = (Rsa2048.mul(p1, p2), st.get)
        }
      }
    }
    r
  }
  /////////////////////累加器的带证明的删除操作（带证明的删除、删除之后输出证明）----结束/////////////////////////


  /////////////////////累加器的非成员证明与验证操作----开始/////////////////////////
  def nonmemberShipProof(elements: Array[Array[Byte]]): NonmembershipProof ={
    var r: NonmembershipProof = null
    var x: BigInteger = hash_prime_product(elements)
    val s = this.acc_aggregate_value
    val beZout = Util.Bezout(x, s)
    if (beZout != None) {
      val zout = beZout.get
      if (zout.gcd.compareTo(BigInteger.ONE) == 0) {
        val d = Rsa2048.exp(this.acc_base_value, zout.coefficient_a)
        val v = Rsa2048.exp(this.acc_value, zout.coefficient_b)
        val gv_inv = Rsa2048.op(this.acc_base_value, Rsa2048.inv(v))
        val w = NonWitness(d, v, gv_inv)
        val poke2_proof = Poke2.prove(this.acc_value,zout.coefficient_b,v,bitLength,hashTool)
        val poe_proof = Poe.prove(d,x,gv_inv,bitLength,hashTool)
        r = NonmembershipProof(w,poe_proof,poke2_proof)
      }
    }
    r
  }

  def nonmemberShipVerify(elements: Array[Array[Byte]],proof:NonmembershipProof):Boolean  ={
    var x: BigInteger = hash_prime_product(elements)
    Poke2.verify(this.acc_value,proof.nonWitness.v_coefficient,proof.proof_poke2,bitLength,hashTool) &&
      Poe.verify(proof.nonWitness.d_coefficient,x,proof.nonWitness.gv_inv,proof.proof_poe,bitLength,hashTool)
  }
  /////////////////////累加器的非成员证明操作----结束/////////////////////////

  /////////////////////累加器的成员证明更新操作，更新到当前累加----开始/////////////////////////
  def updateMembershipWitness(tracked_element:Array[Array[Byte]],witness:Witness,
                              untracked_added:Array[Array[Byte]],
                              untracked_deleted:Array[Array[Byte]]):Witness={
    var r : Witness = null
    val x = hash_prime_product(tracked_element)
    val x_hat = hash_prime_product(untracked_deleted);

    var isExcept:Boolean = false
    breakable(
      tracked_element.foreach(e=>{
        if(untracked_added.contains(e) || untracked_deleted.contains(e)){
          isExcept = true
          break
        }
      })
    )

    if(!isExcept){
      val bzout = Util.Bezout(x,x_hat)
      if(bzout != None){
        val zout = bzout.get
        if(zout.gcd.compareTo(BigInteger.ONE) == 0){
          val acc_w = new Accumulator4Aggregate(this.acc_base_value,witness.promise,witness.agg,this.hashTool)
          val w = acc_w.add(untracked_added)
          val w_to_b = Rsa2048.exp(w.getAccVaule,zout.coefficient_b)
          val acc_new_to_a = Rsa2048.exp(this.getAccVaule,zout.coefficient_a)
          val promise = Rsa2048.op(w_to_b,acc_new_to_a)
          r = Witness(promise,Rsa2048.div(w.getAccAgg,x_hat))
        }
      }
    }
    r
  }
  /////////////////////累加器的成员证明更新操作，更新到当前累加----结束/////////////////////////

  private def hash_prime_product(elements:Array[Array[Byte]]):BigInteger={
    var x: BigInteger = BigInteger.ONE
    for (i <- 0 to elements.length - 1) {
      x = Rsa2048.mul(x, PrimeTool.hash2Prime(elements(i), bitLength, hashTool))
    }
    x
  }

  def verifyMembershipWitness(witness: Witness, member: Array[Byte]): Boolean = {
    val prime = PrimeTool.hash2Prime(member, bitLength, hashTool)
    if (Rsa2048.exp(witness.promise, prime).compareTo(this.getAccVaule) == 0) true else false
  }

  /**
   * 这个方法需要进一步修改
   * */
  def membershipWitness1(member: Array[Byte]): Witness = {
    val prime = PrimeTool.hash2Prime(member, bitLength, hashTool)
    Witness(Rsa2048.exp(this.acc_base_value, Rsa2048.div(this.acc_aggregate_value, prime)),this.getAccAgg)
  }



  /*def membershipWitness(member: Array[Byte]): Witness = {

    val prime = PrimeTool.hash2Prime(member, bitLength, hashTool)
    Witness(Rsa2048.exp(this.acc_base_value, Rsa2048.div(this.acc_aggregate_value, prime)), this.acc_value)
  }*/

  def root_factor(g: BigInteger, elems: ArrayBuffer[BigInteger]):ArrayBuffer[BigInteger] = {
    if(elems.length == 1){
      val ab = new ArrayBuffer[BigInteger]()
      ab += g
      ab
    }else{
      val half = elems.length / 2

      var g_left = g
      for(i<-0 to half-1){
        g_left = Rsa2048.exp(g_left,elems(i))
      }

      var g_right = g
      for(i<-half to elems.length-1){
        g_right = Rsa2048.exp(g_right,elems(i))
      }
      val left = root_factor(g_right,elems.slice(0,half))
      val right = root_factor(g_left,elems.slice(half,elems.length))
      left.appendAll(right)
      left
    }
  }



  private def isAggregate(prime: BigInteger): Boolean = {
    val agg = Rsa2048.divideAndRemainder(this.acc_aggregate_value, prime)
    if (agg._2.compareTo(BigInteger.ZERO) == 0 && agg._1.compareTo(BigInteger.ONE) > 0) {
      true
    } else {
      false
    }
  }




}
