package rep.accumulator.verkle

import rep.accumulator.Accumulator.{MembershipProof, NonMembershipProof, Witness}
import rep.accumulator.{Accumulator, PrimeTool, Rsa2048}
import rep.accumulator.verkle.MiddleNode.{ChildIdentifier, MiddleNodeAccValuesPrefix, MiddleNodePrimeValuesPrefix, MiddleNodeValuesPrefix, VerkleProofOfMembership, VerkleProofOfNonMembership, degree}
import rep.accumulator.verkle.util.verkleTool
import rep.app.system.RepChainSystemContext
import rep.storage.db.factory.DBFactory
import rep.utils.SerializeUtils
import java.math.BigInteger
import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks.{break, breakable}

object MiddleNode {
  case class ChildIdentifier(id: Array[Int], childType: Int, acc_value: BigInteger, prime: BigInteger) //childType 0=leafnode;1=middlenode

  case class VerkleProofOfMembership(nodeId: String, acc_value: BigInteger, proof: MembershipProof)

  case class VerkleProofOfNonMembership(nodeId: String, acc_value: BigInteger, proof: NonMembershipProof)

  val degree = 256
  val MiddleNodeValuesPrefix = "m_n_v_"
  val MiddleNodeAccValuesPrefix = "m_n_a_v_"
  val MiddleNodePrimeValuesPrefix = "m_n_p_v_"
}

class MiddleNode(ctx: RepChainSystemContext, keyPrefix: Array[Int]) {
  private val db = DBFactory.getDBAccess(ctx.getConfig)
  private var children: Array[ChildIdentifier] = null
  private var acc_value: BigInteger = null
  private var prime_value: BigInteger = BigInteger.ONE

  loader

  private def getKeyPrefixLength: Int = {
    if (keyPrefix == null) {
      0
    } else {
      keyPrefix.length
    }
  }

  private def linkKeyPrefix(a: Array[Int]): Array[Int] = {
    if (keyPrefix == null) {
      a
    } else {
      keyPrefix ++ a
    }
  }

  def loader: Unit = {
    val v = db.getObject[Array[ChildIdentifier]](MiddleNodeValuesPrefix + verkleTool.getKey(keyPrefix))
    this.children = if (v != None) v.get else new Array[ChildIdentifier](degree)
    val av = db.getObject[BigInteger](MiddleNodeAccValuesPrefix + verkleTool.getKey(keyPrefix))
    if (av != None) this.acc_value = av.get
    val pv = db.getObject[BigInteger](MiddleNodePrimeValuesPrefix + verkleTool.getKey(keyPrefix))
    if (pv != None) this.prime_value = pv.get
  }

  private def updateToDB: Unit = {
    db.putBytes(MiddleNodeValuesPrefix + verkleTool.getKey(keyPrefix), SerializeUtils.serialise(this.children))
    db.putBytes(MiddleNodeAccValuesPrefix + verkleTool.getKey(keyPrefix), SerializeUtils.serialise(this.acc_value))
    db.putBytes(MiddleNodePrimeValuesPrefix + verkleTool.getKey(keyPrefix), SerializeUtils.serialise(this.prime_value))
  }

  def addState(key: Array[Int], data: Array[Byte], prime: BigInteger): Unit = {
    val idx = key(getKeyPrefixLength)
    val obj = this.children(idx)
    if (obj != null) {
      //存在节点
      if (obj.childType == 0) {
        //已经存在叶子节点，继续添加
        val leaf = this.ctx.getVerkleNodeBuffer.readLeafNode(obj.id)
        if (!leaf.isExist(prime)) {
          val md = this.addMiddleNode(idx)
          md.addLeafNode(key, data, prime)
          md.addLeafNode(obj.id, leaf)
        } else {
          System.out.println(s"key=${verkleTool.getKey(key)} is Exist")
        }
      } else if (obj.childType == 1) {
        //属于中间节点，继续递归
        val child = this.ctx.getVerkleNodeBuffer.readMiddleNode(obj.id)
        child.addState(key, data, prime)
      }
    } else {
      //不存在节点，直接添加叶子节点
      this.addLeafNode(key, data, prime)
    }
  }

  private def addMiddleNode(idx: Int): MiddleNode = {
    val vb = this.ctx.getVerkleNodeBuffer
    val md = vb.readMiddleNode(this.linkKeyPrefix(Array(idx)))
    md
  }

  private def addLeafNode(key: Array[Int], data: Array[Byte]): LeafNode = {
    val p = PrimeTool.hash2Prime(ctx.getHashTool.hash(data), Accumulator.bitLength, ctx.getHashTool)
    addLeafNode(key, data, p)
  }

  private def addLeafNode(key: Array[Int], data: Array[Byte], prime: BigInteger): LeafNode = {
    val vb = this.ctx.getVerkleNodeBuffer
    val leaf = vb.readLeafNode(key)
    val acc_value = leaf.add(data, prime)
    val identifier = ChildIdentifier(key, 0, acc_value,
      PrimeTool.hash2Prime(ctx.getHashTool.hash(acc_value.toByteArray), Accumulator.bitLength, ctx.getHashTool))
    updateMiddleNode(key(getKeyPrefixLength), identifier)
    //更新父节点
    val md = this.getParentNode
    if (md != null) md.updateAcc(key(getKeyPrefixLength - 1), this.acc_value)
    leaf
  }

  private def addLeafNode(key: Array[Int], leaf: LeafNode): LeafNode = {
    val identifier = ChildIdentifier(key, 0, acc_value,
      PrimeTool.hash2Prime(ctx.getHashTool.hash(acc_value.toByteArray), Accumulator.bitLength, ctx.getHashTool))
    updateMiddleNode(key(getKeyPrefixLength), identifier)
    //更新父节点
    val md = this.getParentNode
    if (md != null) md.updateAcc(key(getKeyPrefixLength - 1), this.acc_value)
    leaf
  }

  def getParentNode: MiddleNode = {
    if (this.keyPrefix == null) {
      //当前是根节点
      null
    } else {
      val vb = this.ctx.getVerkleNodeBuffer
      if (this.keyPrefix.length == 1) {
        vb.readMiddleNode(null)
      } else if (this.keyPrefix.length > 1) {
        val prefix = this.keyPrefix.slice(0, this.keyPrefix.length - 1)
        vb.readMiddleNode(prefix)
      } else {
        null
      }
    }
  }

  private def updateAcc(idx: Int, acc: BigInteger): Unit = {
    val p = PrimeTool.hash2Prime(ctx.getHashTool.hash(acc.toByteArray), Accumulator.bitLength, ctx.getHashTool)
    val ci = ChildIdentifier(this.linkKeyPrefix(Array(idx)), 1, acc, p)
    updateMiddleNode(idx, ci)
    val md = this.getParentNode
    if (md != null) {
      val index = this.keyPrefix(this.getKeyPrefixLength - 1)
      md.updateAcc(index, this.acc_value)
    }
  }

  private def updateMiddleNode(idx: Int, ci: ChildIdentifier): Unit = {
    val old_identifier = this.children(idx)
    val acc_new = if (old_identifier == null) {
      updateAccValue(null, ci.prime, this.prime_value, this.acc_value)
    } else {
      updateAccValue(old_identifier.prime, ci.prime, this.prime_value, this.acc_value)
    }
    this.prime_value = acc_new._1
    this.acc_value = acc_new._2
    this.children(idx) = ci
    this.updateToDB
  }

  private def updateAccValue(old_prime: BigInteger, new_prime: BigInteger, primes: BigInteger,
                             acc: BigInteger): Tuple2[BigInteger, BigInteger] = {
    var v_primes = BigInteger.ONE
    if (old_prime != null) {
      val dr = Rsa2048.divideAndRemainder(primes, old_prime)
      if (dr._2.compareTo(BigInteger.ZERO) == 0) {
        v_primes = dr._1
        System.out.println(s"updateAccValue exist old value:id=${verkleTool.getKey(this.keyPrefix)},prime=${this.prime_value}")
      }
      System.out.println(s"updateAccValue:id=${verkleTool.getKey(this.keyPrefix)},old_prime=${old_prime},new_prime=${new_prime}")
    } else {
      System.out.println(s"updateAccValue:id=${verkleTool.getKey(this.keyPrefix)},new_prime1=${new_prime}")
      v_primes = this.prime_value
    }
    v_primes = Rsa2048.mul(v_primes, new_prime)
    val new_acc = Rsa2048.exp(this.ctx.getStateAccBase, v_primes)
    System.out.println(s"updateAccValue:id=${verkleTool.getKey(this.keyPrefix)},prime=${v_primes}")
    new Tuple2[BigInteger, BigInteger](v_primes, new_acc)
  }

  def middleToString: String = {
    val sb = new StringBuffer()
    sb.append(s"node-type=middle\tkeyprefix=${verkleTool.getKey(this.keyPrefix)} node-name=${verkleTool.getKey(this.keyPrefix)},")
    sb.append(s"\tacc_value=${acc_value}").append(s"\tprime-product=${this.prime_value}")
    for (i <- 0 to this.children.length - 1) {
      sb.append("\r").append("\t")
      if (this.children(i) != null) {
        sb.append(s"son(${i})\t")
        if (this.children(i).childType == 0) {
          val l = this.ctx.getVerkleNodeBuffer.readLeafNode(this.children(i).id)
          sb.append(l.leafToString)
        } else {
          val m = this.ctx.getVerkleNodeBuffer.readMiddleNode(this.children(i).id)
          sb.append("\r")
          val tmp = m.middleToString
          sb.append(tmp)
        }
      }
    }
    sb.toString
  }

  def getProofs(key: Array[Int], data: Array[Byte], prime: BigInteger): Array[Any] = {
    val proofs: ArrayBuffer[Any] = new ArrayBuffer[Any]()
    val idx = key(getKeyPrefixLength)
    val obj = this.children(idx)
    if (obj != null) {
      //存在节点
      val vb = this.ctx.getVerkleNodeBuffer
      val p = getMembershipProof(obj.prime)
      proofs += p
      if (obj.childType == 0) {
        //已经存在叶子节点，继续添加
        val leaf = vb.readLeafNode(obj.id)
        if (leaf.isExist(prime)) {
          //存在，输出存在证明，输出叶子存在证明
          val p = leaf.getMembershipProof(prime)
          proofs += p
        } else {
          //不存在，输出不存在证明，输出叶子不存在证明
          val p = leaf.getNonMembershipProof(prime)
          proofs += p
        }
      } else if (obj.childType == 1) {
        //属于中间节点，输出中间证明，继续递归
        val p = getMembershipProof(obj.prime)
        proofs += p
        val child = this.ctx.getVerkleNodeBuffer.readMiddleNode(obj.id)
        val ps = child.getProofs(key, data, prime)
        proofs += ps.toArray
      }
    } else {
      //不存在节点，输出不存在证明
      val p = getNonMembershipProof(prime)
      proofs += p
    }
    proofs.toArray
  }

  def verifyProofs(element: BigInteger, proofs: Array[Any]): Boolean = {
    var r = true
    if (proofs == null) {
      r = false
      System.out.println("no proof data")
    } else {
      var isExcept = false
      breakable(
        for (i <- 0 to proofs.length - 1) {
          val obj = proofs(i)
          if (obj.isInstanceOf[VerkleProofOfMembership]) {
            val p = obj.asInstanceOf[VerkleProofOfMembership]
            val b = Accumulator.verifyMembershipProof(element, p, ctx.getHashTool)
            if (!b) {
              isExcept = true
              break
            }
          } else if (obj.isInstanceOf[VerkleProofOfNonMembership]) {
            val p = obj.asInstanceOf[VerkleProofOfNonMembership]
            val b = Accumulator.VerifyNonMemberShip(Array(element), p, ctx.getHashTool)
            if (!b) {
              isExcept = true
              break
            }
          }
        }
      )
      if (!isExcept) {
        r = true
      }
    }
    r
  }

  def getMembershipProof(prime: BigInteger): VerkleProofOfMembership = {
    val acc = new Accumulator(ctx.getStateAccBase, this.acc_value, ctx.getHashTool)
    val p = Rsa2048.div(this.prime_value, prime)
    val wit = Rsa2048.exp(ctx.getStateAccBase, p)
    val proof = acc.getMemberProof4Witness(prime, Witness(wit))
    VerkleProofOfMembership(verkleTool.getKey(this.keyPrefix), this.acc_value, proof.proof)
  }

  def getNonMembershipProof(prime: BigInteger): VerkleProofOfNonMembership = {
    val acc = new Accumulator(ctx.getStateAccBase, this.acc_value, ctx.getHashTool)
    VerkleProofOfNonMembership(verkleTool.getKey(this.keyPrefix), this.acc_value, acc.nonmemberShipProof(Array(this.prime_value), Array(prime)))
  }
}
