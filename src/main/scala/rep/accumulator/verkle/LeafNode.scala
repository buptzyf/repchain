package rep.accumulator.verkle


import rep.accumulator.{Accumulator, PrimeTool}
import rep.accumulator.verkle.LeafNode.{LeafNodeAccValuePrefix, LeafNodeAccWitPrefix, LeafNodePrimesPrefix, LeafNodeValuesPrefix}
import rep.accumulator.verkle.util.verkleTool
import rep.app.system.RepChainSystemContext
import rep.storage.db.factory.DBFactory
import rep.utils.SerializeUtils

import java.math.BigInteger
import scala.collection.mutable.ArrayBuffer

object LeafNode{
  val LeafNodeValuesPrefix = "a_l_v_"
  val LeafNodePrimesPrefix = "a_l_p_"
  val LeafNodeAccValuePrefix = "a_l_a_"
  val LeafNodeAccWitPrefix = "a_l_w_"
}

class LeafNode(ctx:RepChainSystemContext, nodeId:Array[Int]){
  private val db = DBFactory.getDBAccess(ctx.getConfig)
  private var values:ArrayBuffer[Array[Byte]] = null
  private var primes:ArrayBuffer[BigInteger] = null
  private var acc_value:BigInteger = null
  private var last_witness:BigInteger = null
  private val node_name = verkleTool.getKey(nodeId)

  loader

   def leafToString:String={
    val sb = new StringBuffer()
    sb.append(s"node-type=leaf\tnode-name=${node_name},")
    sb.append(s"\tacc_value=${acc_value}").append(s"\twitness=${last_witness}")
    sb.toString
  }

  private def getNodeId:Array[Int]={
    this.nodeId
  }

  private def loader:Unit={
    val v = db.getObject[ArrayBuffer[Array[Byte]]](LeafNodeValuesPrefix+node_name)
    if(v != None) this.values = v.get else this.values = new ArrayBuffer[Array[Byte]]()
    val p = db.getObject[ArrayBuffer[BigInteger]](LeafNodePrimesPrefix+node_name)
    if(p != None) this.primes = p.get else this.primes = new ArrayBuffer[BigInteger]()
    val av = db.getObject[BigInteger](LeafNodeAccValuePrefix+node_name)
    if(av != None) this.acc_value = av.get
    val lw = db.getObject[BigInteger](LeafNodeAccWitPrefix+node_name)
    if(lw != None) this.last_witness = lw.get
  }

  private def update:Unit={
    db.putBytes(LeafNodeValuesPrefix + node_name,SerializeUtils.serialise(this.values))
    db.putBytes(LeafNodePrimesPrefix + node_name,SerializeUtils.serialise(this.primes))
    db.putBytes(LeafNodeAccValuePrefix + node_name,SerializeUtils.serialise(this.acc_value))
    db.putBytes(LeafNodeAccWitPrefix + node_name,SerializeUtils.serialise(this.last_witness))
  }

  def add(v:Array[Byte]):BigInteger={
    val p = PrimeTool.hash2Prime(v,Accumulator.bitLength,ctx.getHashTool)
    add(v,p)
  }

  def add(v:Array[Byte],p:BigInteger):BigInteger={
    if(!this.primes.contains(p)){
      val acc = new Accumulator(ctx.getStateAccBase, this.acc_value, ctx.getHashTool)
      val new_acc = acc.add(p)
      if (new_acc != null) {
        this.primes += p
        this.values += v
        if (this.acc_value == null) {
          this.last_witness = ctx.getStateAccBase
        } else {
          this.last_witness = this.acc_value
        }
        this.acc_value = new_acc.getAccVaule
        this.update
      }
      this.acc_value
    }else{
      this.acc_value
    }
  }

  def hasValue:Boolean={
    this.values.length > 0
  }

  def isExist(prime:BigInteger):Boolean={
    if(this.primes.contains(prime)){
      true
    }else{
      false
    }
  }

  def getWitness:BigInteger={
    if(this.last_witness != null) this.last_witness else null
  }
}
