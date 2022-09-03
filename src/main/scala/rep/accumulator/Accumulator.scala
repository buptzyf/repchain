package rep.accumulator

import java.math.BigInteger
import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks.{break, breakable}
import rep.crypto.Sha256
import rep.accumulator.Accumulator.{
  AccumulatorWithMembershipProof, MembershipProof,
  NonWitness, NonMembershipProof, Witness, bitLength
}

object Accumulator{
  val bitLength = 256

  case class NonWitness(d_coefficient: BigInteger, v_coefficient: BigInteger, gv_inv: BigInteger)

  case class Witness(witness: BigInteger)

  case class MembershipProof(proof: BigInteger, witness: Witness)

  case class AccumulatorWithMembershipProof(acc: Accumulator, proof: MembershipProof)

  case class NonMembershipProof(nonWitness: NonWitness, proof_poe: BigInteger, proof_poke2: Poke2.proofStruct)
}

class Accumulator (acc_base: BigInteger, last_acc: BigInteger, hashTool: Sha256) {
  private var acc_value: BigInteger = last_acc
  private var acc_base_value: BigInteger = acc_base

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

  }

  def copy: Accumulator = {
    new Accumulator(this.acc_base_value, this.acc_value, this.hashTool)
  }

  def getAccVaule: BigInteger = {
    this.acc_value
  }

  def getAccBase: BigInteger = {
    this.acc_base_value
  }

  /////////////////////累加器的累加操作（累加，累加之后输出证明）、验证证明----开始/////////////////////////
  def add(element: Array[Byte]): Accumulator = {
    val prime = PrimeTool.hash2Prime(element, bitLength, hashTool)
    add(prime)
  }

  def add(element: BigInteger): Accumulator = {
    val new_acc_value = if (this.acc_value.compareTo(BigInteger.ZERO) == 0) {
      Rsa2048.exp(this.acc_base_value, element)
    } else {
      Rsa2048.exp(this.acc_value, element)
    }
    new Accumulator(this.acc_base_value, new_acc_value, this.hashTool)
  }

  def addOfBatch(elements: Array[Array[Byte]]): Accumulator = {
    var acc : Accumulator = null
    val buf = hash_primes(elements)
    addOfBatch(buf.toArray)
  }

  def addOfBatch(elements: Array[BigInteger]): Accumulator = {
    var agg = product(elements)
    add(agg)
  }

  def addAndProof(element: Array[Byte]):AccumulatorWithMembershipProof = {
    val prime = PrimeTool.hash2Prime(element, bitLength, hashTool)
    addAndProof(prime)
  }

  def addAndProof(element: BigInteger): AccumulatorWithMembershipProof = {
    val new_acc = add(element)
    val proof = Poe.prove(this.acc_value, element, new_acc.getAccVaule, bitLength, hashTool)
    AccumulatorWithMembershipProof(new_acc, MembershipProof(proof, Witness(this.acc_value)))
  }

  def addAndProof(elements: Array[BigInteger]): AccumulatorWithMembershipProof = {
    val p = product(elements)
    addAndProof(p)
  }

  def addAndProof(elements: Array[Array[Byte]]): AccumulatorWithMembershipProof = {
    val primes = hash_primes(elements)
    addAndProof(primes)
  }

  def verifyMembershipProof(member: Array[Byte],proof:MembershipProof): Boolean = {
    val prime = PrimeTool.hash2Prime(member, bitLength, hashTool)
    verifyMembershipProof(prime, proof: MembershipProof)
  }

  def verifyMembershipProof(member: BigInteger, proof: MembershipProof): Boolean = {
    Poe.verify(proof.witness.witness, member, this.acc_value, proof.proof, bitLength, hashTool)
  }

  def verifyMembershipProofOfBatch(members: Array[BigInteger], proof: MembershipProof): Boolean = {
    val p = product(members)
    verifyMembershipProof(p, proof)
  }

  def verifyMembershipProofOfBatch(members: Array[Array[Byte]], proof: MembershipProof): Boolean = {
    val ps = hash_primes(members)
    verifyMembershipProofOfBatch(ps, proof)
  }

  def verifyMembershipWitness(witness: Witness, member: Array[Byte]): Boolean = {
    val prime = PrimeTool.hash2Prime(member, bitLength, hashTool)
    if (Rsa2048.exp(witness.witness, prime).compareTo(this.getAccVaule) == 0) true else false
  }

  def membershipWitness(member: Array[Byte],witness: Witness): Witness = {
    val prime = PrimeTool.hash2Prime(member, bitLength, hashTool)
    membershipWitness(prime, witness)
  }

  def membershipWitness(member: BigInteger, witness: Witness): Witness = {
    val acc = deleteWithWitness(member,witness)
    Witness(acc.getAccVaule)
  }
  /////////////////////累加器的累加操作（累加，累加之后输出证明）、验证证明----结束/////////////////////////


  /////////////////////累加器的带证明的删除操作（带见证的删除、删除之后输出证明）----开始/////////////////////////
  def deleteWithWitness(element: Array[Byte], witness: Witness): Accumulator = {
    val prime = PrimeTool.hash2Prime(element, bitLength, hashTool)
    deleteWithWitness(prime, witness: Witness)
  }

  def deleteWithWitness(element: BigInteger, witness: Witness): Accumulator = {
    val buf: ArrayBuffer[(BigInteger, BigInteger)] = new ArrayBuffer[(BigInteger, BigInteger)]()
    buf += Tuple2(element, witness.witness)
    delete(buf.toArray)
  }

  def deleteWithWitnessToProof(element: Array[Byte], witness: Witness): AccumulatorWithMembershipProof = {
    val prime = PrimeTool.hash2Prime(element, bitLength, hashTool)
    deleteWithWitnessToProof(prime, witness)
  }

  def deleteWithWitnessToProof(element: BigInteger, witness: Witness): AccumulatorWithMembershipProof = {
    val buf: ArrayBuffer[(BigInteger, BigInteger)] = new ArrayBuffer[(BigInteger, BigInteger)]()
    buf += Tuple2(element, witness.witness)
    val acc = delete(buf.toArray)
    val proof = Poe.prove(acc.getAccVaule, element, this.acc_value, bitLength, hashTool)
    AccumulatorWithMembershipProof(acc, MembershipProof(proof, Witness(acc.getAccVaule)))
  }

  private def delete(elements: Array[(BigInteger, BigInteger)]): Accumulator = {
    var r: Accumulator = null
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
        r = new Accumulator(this.acc_base_value, st._2, this.hashTool)
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
  def nonmemberShipProof(acc_set: Array[BigInteger],nonElements: Array[BigInteger] ): NonMembershipProof = {
    val a_s: BigInteger = product(acc_set)
    val n_e : BigInteger = product(nonElements)
    nonmemberShipProof(a_s, n_e)
  }

  def nonmemberShipProof(acc_set: Array[Array[Byte]], nonElements: Array[Array[Byte]]): NonMembershipProof = {
    val acc_set_ps = hash_primes(acc_set)
    val n_e_ps = hash_primes(nonElements)
    nonmemberShipProof(acc_set_ps,n_e_ps)
  }

  def nonmemberShipProof(acc: BigInteger, non: BigInteger): NonMembershipProof = {
    var r: NonMembershipProof = null
    val x = non
    val s = acc
    val beZout = Util.Bezout(x,s)
    if (beZout != None) {
      val zout = beZout.get
      if (zout.gcd.compareTo(BigInteger.ONE) == 0) {
        val g =  this.acc_base_value
        val d = if(zout.sign_a) Rsa2048.exp( Rsa2048.inv(g), zout.coefficient_a) else Rsa2048.exp(g, zout.coefficient_a)
        val v = if(zout.sign_b) Rsa2048.exp( Rsa2048.inv(this.acc_value), zout.coefficient_b) else Rsa2048.exp(this.acc_value, zout.coefficient_b)
        val gv_inv = Rsa2048.op(g, Rsa2048.inv(v))
        val w = NonWitness(d, v, gv_inv)
        val tr = Rsa2048.exp(d,x)
        val poke2_proof = Poke2.prove(this.acc_value, zout.coefficient_b, v, bitLength, hashTool)
        val poe_proof = Poe.prove(d, x, gv_inv, bitLength, hashTool)
        r = NonMembershipProof(w, poe_proof, poke2_proof)
      }
    }
    r
  }


  def nonmemberShipVerify(nonElements: Array[Array[Byte]],proof:NonMembershipProof):Boolean  ={
    val x = hash_primes(nonElements)
    nonmemberShipVerify(x, proof)
  }

  def nonmemberShipVerify(nonElements: Array[BigInteger], proof: NonMembershipProof): Boolean = {
    val x: BigInteger = product(nonElements)
    Poe.verify(proof.nonWitness.d_coefficient, x, proof.nonWitness.gv_inv, proof.proof_poe, bitLength, hashTool) &&
    Poke2.verify(this.acc_value, proof.nonWitness.v_coefficient, proof.proof_poke2, bitLength, hashTool)
  }
  /////////////////////累加器的非成员证明操作----结束/////////////////////////

  /////////////////////累加器的成员证明更新操作，更新到当前累加----开始/////////////////////////
  def updateMembershipWitness(tracked_element:Array[Array[Byte]],witness:Witness,
                              untracked_added:Array[Array[Byte]],
                              untracked_deleted:Array[Array[Byte]]):Witness={
    val tracked_elements = hash_primes(tracked_element)
    val untracked_addeds = hash_primes(untracked_added)
    val untracked_deleteds = hash_primes(untracked_deleted)
    updateMembershipWitness(tracked_elements, witness: Witness, untracked_addeds, untracked_deleteds)
  }

  def updateMembershipWitness(tracked_element: Array[BigInteger], witness: Witness,
                              untracked_added: Array[BigInteger],
                              untracked_deleted: Array[BigInteger]): Witness = {
    var r: Witness = null
    val x = product(tracked_element)
    val x_hat = product(untracked_deleted);

    var isExcept: Boolean = false
    breakable(
      tracked_element.foreach(e => {
        if (untracked_added.contains(e) || untracked_deleted.contains(e)) {
          isExcept = true
          break
        }
      })
    )

    if (!isExcept) {
      val bzout = Util.Bezout(x, x_hat)
      if (bzout != None) {
        val zout = bzout.get
        if (zout.gcd.compareTo(BigInteger.ONE) == 0) {
          val acc_w = new Accumulator(this.acc_base_value, witness.witness, this.hashTool)
          val w = acc_w.addOfBatch(untracked_added)
          val w_to_b = if(zout.sign_b) Rsa2048.exp(Rsa2048.inv(w.getAccVaule), zout.coefficient_b) else Rsa2048.exp(w.getAccVaule, zout.coefficient_b)
          val acc_new_to_a = if(zout.sign_a) Rsa2048.exp(Rsa2048.inv(this.getAccVaule), zout.coefficient_a) else Rsa2048.exp(this.getAccVaule, zout.coefficient_a)
          val promise = Rsa2048.op(w_to_b, acc_new_to_a)
          r = Witness(promise)
        }
      }
    }
    r
  }

  /////////////////////累加器的成员证明更新操作，更新到当前累加----结束/////////////////////////

  /////////////////////求当前累加器之上的所有累加元素的见证----开始/////////////////////////
  def root_factor(g: BigInteger, elems: Array[Array[Byte]]): Array[BigInteger] = {
    val es = hash_primes(elems)
    root_factor(g, es)
  }

  def root_factor(g: BigInteger, elems: Array[BigInteger]): Array[BigInteger] = {
    if (elems.length == 1) {
      val ab = new ArrayBuffer[BigInteger]()
      ab += g
      ab.toArray
    } else {
      val half = elems.length / 2

      var g_left = g
      for (i <- 0 to half - 1) {
        g_left = Rsa2048.exp(g_left, elems(i))
      }

      var g_right = g
      for (i <- half to elems.length - 1) {
        g_right = Rsa2048.exp(g_right, elems(i))
      }
      val left = root_factor(g_right, elems.slice(0, half))
      val right = root_factor(g_left, elems.slice(half, elems.length))
      left ++ right
    }
  }
  /////////////////////求当前累加器之上的所有累加元素的见证----结束/////////////////////////

  /////////////////////公共方法----开始/////////////////////////
  def hash_prime_product(elements:Array[Array[Byte]]):BigInteger={
    var x: BigInteger = BigInteger.ONE
    for (i <- 0 to elements.length - 1) {
      x = Rsa2048.mul(x, PrimeTool.hash2Prime(elements(i), bitLength, hashTool))
    }
    x
  }

  def product(elements: Array[BigInteger]): BigInteger = {
    var x: BigInteger = BigInteger.ONE
    elements.foreach(e=>{
      x = Rsa2048.mul(x, e)
    })
    x
  }

  def hash_primes(elements:Array[Array[Byte]]):Array[BigInteger]={
    val buf = new ArrayBuffer[BigInteger]()
    elements.foreach(e => {
      val prime = PrimeTool.hash2Prime(e, bitLength, hashTool)
      buf += prime
    })
    buf.toArray
  }
  /////////////////////公共方法----结束/////////////////////////

}

