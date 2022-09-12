package rep.accumulator.verkle

import rep.accumulator.{Accumulator, PrimeTool, Rsa2048}
import rep.accumulator.verkle.MiddleNode.{ChildIdentifier, FindResult, MiddleNodeAccValuesPrefix, MiddleNodePrimeValuesPrefix, MiddleNodeValuesPrefix, degree}
import rep.accumulator.verkle.util.verkleTool
import rep.app.system.RepChainSystemContext
import rep.storage.db.factory.DBFactory
import rep.utils.SerializeUtils
import java.math.BigInteger

object MiddleNode{
  case class ChildIdentifier(id:Array[Int],childType:Int,acc_value:BigInteger,prime:BigInteger) //childType 0=leafnode;1=middlenode
  case class FindResult(hasFind:Boolean,obj:Any,childType:Int)
  val degree = 256
  val MiddleNodeValuesPrefix = "m_n_v_"
  val MiddleNodeAccValuesPrefix = "m_n_a_v_"
  val MiddleNodePrimeValuesPrefix = "m_n_p_v_"
}

class MiddleNode(ctx:RepChainSystemContext,keyPrefix:Array[Int]) {
  private val db = DBFactory.getDBAccess(ctx.getConfig)
  private var children : Array[ChildIdentifier] = null
  private var acc_value: BigInteger = null
  private var prime_value:BigInteger = BigInteger.ONE

  loader

  private def getKeyPrefixLength:Int={
    if(keyPrefix == null){
      0
    }else{
      keyPrefix.length
    }
  }

  private def linkKeyPrefix(a:Array[Int]):Array[Int]={
    if(keyPrefix == null){
      a
    }else{
      keyPrefix ++ a
    }
  }

  def loader:Unit= {
    val v = db.getObject[Array[ChildIdentifier]](MiddleNodeValuesPrefix + verkleTool.getKey(keyPrefix))
    this.children = if (v != None) v.get else new Array[ChildIdentifier](degree)
    val av = db.getObject[BigInteger](MiddleNodeAccValuesPrefix + verkleTool.getKey(keyPrefix))
    if (av != None) this.acc_value = av.get
    val pv = db.getObject[BigInteger](MiddleNodePrimeValuesPrefix + verkleTool.getKey(keyPrefix))
    if (pv != None) this.prime_value = pv.get
  }

  private def updateToDB:Unit={
    db.putBytes(MiddleNodeValuesPrefix + verkleTool.getKey(keyPrefix),SerializeUtils.serialise(this.children))
    db.putBytes(MiddleNodeAccValuesPrefix + verkleTool.getKey(keyPrefix), SerializeUtils.serialise(this.acc_value))
    db.putBytes(MiddleNodePrimeValuesPrefix + verkleTool.getKey(keyPrefix), SerializeUtils.serialise(this.prime_value))
  }

  def addState(key:Array[Int],data:Array[Byte],prime:BigInteger):Unit={
    val idx = key(getKeyPrefixLength)
    val obj = this.children(idx)
    if(obj != null){
      //存在节点
      if(obj.childType == 0){
        //已经存在叶子节点，继续添加
        val leaf = this.ctx.getVerkleNodeBuffer.readLeafNode(obj.id)
        if(!leaf.isExist(prime)){
          val md = this.addMiddleNode(idx)
          md.addLeafNode(key, data, prime)
          md.addLeafNode(obj.id, leaf)
        }else{
          System.out.println(s"key=${verkleTool.getKey(key)} is Exist")
        }
      }else if(obj.childType == 1){
        //属于中间节点，继续递归
        val child = this.ctx.getVerkleNodeBuffer.readMiddleNode(obj.id)
        child.addState(key,data,prime)
      }
    }else{
      //不存在节点，直接添加叶子节点
      this.addLeafNode(key, data, prime)
    }
  }

  private def addMiddleNode(idx:Int):MiddleNode={
    val vb = this.ctx.getVerkleNodeBuffer
    val md = vb.readMiddleNode(this.linkKeyPrefix(Array(idx)))
    md
  }

  private def addLeafNode(key:Array[Int],data:Array[Byte]):LeafNode={
    val p = PrimeTool.hash2Prime(ctx.getHashTool.hash(data),Accumulator.bitLength,ctx.getHashTool)
    addLeafNode(key,data,p)
  }

  private def addLeafNode(key:Array[Int],data:Array[Byte],prime:BigInteger):LeafNode={
    val vb = this.ctx.getVerkleNodeBuffer
    val leaf = vb.readLeafNode(key)
    val acc_value = leaf.add(data,prime)
    val identifier = ChildIdentifier(key,0,acc_value,
      PrimeTool.hash2Prime(ctx.getHashTool.hash(acc_value.toByteArray),Accumulator.bitLength,ctx.getHashTool))
    updateMiddleNode(key(getKeyPrefixLength),identifier)
    //更新父节点
    val md = this.getParentNode
    if(md != null) md.updateAcc(key(getKeyPrefixLength-1),this.acc_value)
    leaf
  }

  private def addLeafNode(key: Array[Int], leaf:LeafNode): LeafNode = {
    val identifier = ChildIdentifier(key, 0, acc_value,
      PrimeTool.hash2Prime(ctx.getHashTool.hash(acc_value.toByteArray), Accumulator.bitLength, ctx.getHashTool))
    updateMiddleNode(key(getKeyPrefixLength), identifier)
    //更新父节点
    val md = this.getParentNode
    if (md != null) md.updateAcc(key(getKeyPrefixLength - 1), this.acc_value)
    leaf
  }

  def getParentNode:MiddleNode={
    if(this.keyPrefix == null){
      //当前是根节点
      null
    }else{
      val vb = this.ctx.getVerkleNodeBuffer
      if (this.keyPrefix.length == 1){
        vb.readMiddleNode(null)
      }else if(this.keyPrefix.length > 1) {
        val prefix = this.keyPrefix.slice(0, this.keyPrefix.length - 1)
        vb.readMiddleNode(prefix)
      } else {
        null
      }
    }
  }

  private def updateAcc(idx:Int,acc:BigInteger):Unit={
    val p = PrimeTool.hash2Prime(ctx.getHashTool.hash(acc.toByteArray),Accumulator.bitLength,ctx.getHashTool)
    val ci = ChildIdentifier(this.linkKeyPrefix(Array(idx)),1,acc,p)
    updateMiddleNode(idx,ci)
    val md = this.getParentNode
    if(md != null){
      val index = this.keyPrefix(this.getKeyPrefixLength-1)
      md.updateAcc(index,this.acc_value)
    }
  }

  private def updateMiddleNode(idx:Int,ci:ChildIdentifier):Unit={
    val old_identifier = this.children(idx)
    val acc_new = if(old_identifier == null){
      updateAccValue(null,ci.prime,this.prime_value, this.acc_value)
    }else{
      updateAccValue(old_identifier.prime,ci.prime,this.prime_value, this.acc_value)
    }
    this.prime_value = acc_new._1
    this.acc_value = acc_new._2
    this.children(idx) = ci
    this.updateToDB
  }

  private def updateAccValue(old_prime:BigInteger,new_prime:BigInteger,primes:BigInteger,
                             acc:BigInteger):Tuple2[BigInteger,BigInteger]={
    var v_primes = BigInteger.ONE
    if(old_prime != null){
      val dr = Rsa2048.divideAndRemainder(primes,old_prime)
      if(dr._2.compareTo(BigInteger.ZERO) == 0){
        v_primes = dr._1
        System.out.println(s"updateAccValue exist old value:id=${verkleTool.getKey(this.keyPrefix)},prime=${this.prime_value}")
      }
      System.out.println(s"updateAccValue:id=${verkleTool.getKey(this.keyPrefix)},old_prime=${old_prime},new_prime=${new_prime}")
    }else{
      System.out.println(s"updateAccValue:id=${verkleTool.getKey(this.keyPrefix)},new_prime1=${new_prime}")
      v_primes = this.prime_value
    }
    v_primes = Rsa2048.mul(v_primes,new_prime)
    val new_acc = Rsa2048.exp(this.ctx.getStateAccBase,v_primes)
    System.out.println(s"updateAccValue:id=${verkleTool.getKey(this.keyPrefix)},prime=${v_primes}")
    new Tuple2[BigInteger,BigInteger](v_primes,new_acc)
  }

  def middleToString:String={
    val sb = new StringBuffer()
    sb.append(s"node-type=middle\tkeyprefix=${verkleTool.getKey(this.keyPrefix)} node-name=${verkleTool.getKey(this.keyPrefix)},")
    sb.append(s"\tacc_value=${acc_value}").append(s"\tprime-product=${this.prime_value}")
    for(i<-0 to this.children.length-1){
      sb.append("\r").append("\t")
      if(this.children(i) != null){
        sb.append(s"son(${i})\t")
        if(this.children(i).childType == 0){
          val l = this.ctx.getVerkleNodeBuffer.readLeafNode(this.children(i).id)
          sb.append(l.leafToString)
        }else{
          val m = this.ctx.getVerkleNodeBuffer.readMiddleNode(this.children(i).id)
          sb.append("\r")
          val tmp = m.middleToString
          sb.append(tmp)
        }
      }
    }
    sb.toString
  }


  /*

  def findNodeFromPath(path:Array[Int]):FindResult={
    var r : FindResult = null
    if(verkleTool.comparePath(this.keyPrefix,path)){
      val idx = path(this.keyPrefix.length)
      val ci = this.children.children(idx)
      if(ci == null){
        //path发现到此终结，不能找到对象,可能需要建立叶子节点
        //返回当前节点，属于中间节点，未来可以在这个节点之下继续建立路径
        r = FindResult(false,this,1)
      }else{
        //发现对象，需要检查是否是叶子节点，还是中间节点，如果是中间节点需要继续发现
        if(ci.childType == 0){
          //叶子节点
          val suffix = verkleTool.getKey(path.slice(this.keyPrefix.length,path.length))
          if(suffix.equals(ci.id)){
            val obj = this.children_obj.getOrDefault(idx.toString,null)
            if(obj == null){
              val tmp = new LeafNode(this, ctx, verkleTool.getKey(keyPrefix) + "-" + suffix)
              this.children_obj.put(idx.toString,tmp)
              r = FindResult(true,tmp,0)
            }else{
              r = FindResult(true,obj,0)
            }
          }else{
            //找到叶子节点，但是名称不匹配
            throw new Exception(s"id not equal suffix,id=${ci.id},suffix=${suffix},path=${util.Arrays.toString(path)}")
          }
        }else{
          //中间节点
          val obj = this.children_obj.getOrDefault(idx.toString, null)
          if (obj == null) {
            val tmp = new MiddleNode(ctx, keyPrefix ++ Array(idx))
            this.children_obj.put(idx.toString,tmp)
            r = tmp.findNodeFromPath(path)
          }else{
            val m_obj = obj.asInstanceOf[MiddleNode]
            r = m_obj.findNodeFromPath(path)
          }
        }
      }
    }else{
      throw new Exception(s"path Exception,path == keyprefix,path=${util.Arrays.toString(path)}")
    }
    r
  }

  def updateLeaf(pos:Int,prime:BigInteger,child_acc_value:BigInteger):Unit={
    val child = this.children.children(pos)
    if(child == null){

    }
  }
*/
}
