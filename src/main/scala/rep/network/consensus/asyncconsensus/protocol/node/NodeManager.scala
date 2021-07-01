package rep.network.consensus.asyncconsensus.protocol.node

import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-03-13
 * @category	管理节点和证书以及私钥。
 */

object NodeManager {
  case class publicKeysOfNode(pubKeyOfFault:String,pubKeyOfDoubleFault:String,pubKeyOfEncrypt:String)
  case class privateKeysOfNode(pkOfFault:String,pkOfDoubleFault:String,pkOfEncrypt:String)
  case class NodeInfo(nodeName:String,nodeAddress:String,pubKey:publicKeysOfNode=null,priKey:privateKeysOfNode=null)

  //存储节点信息，key=node name;value=node address
  private implicit var nodes = new ConcurrentHashMap[String, NodeInfo] asScala

  def addNode(nodeName:String,nodeAddress:String):Unit={
    var v : NodeInfo = null
    if(this.nodes.contains(nodeName)){
      v = this.nodes.getOrElse(nodeName,null)
    }
    if(v == null){
      v = new NodeInfo(nodeName,nodeAddress)
    }else{
      v = new NodeInfo(nodeName,nodeAddress,v.pubKey,v.priKey)
    }

    this.nodes.put(nodeName,v)
  }

  def setPrivateKey(nodeName:String,pkOfFault:String,pkOfDoubleFault:String,pkOfEncrypt:String):Unit={
    var pk = new privateKeysOfNode(pkOfFault,pkOfDoubleFault,pkOfEncrypt)
    var v : NodeInfo = null
    if(this.nodes.contains(nodeName)){
      v = this.nodes.getOrElse(nodeName,null)
    }
    if(v == null){
      v = new NodeInfo(nodeName,"",null,pk)
    }else{
      v = new NodeInfo(nodeName,v.nodeAddress,v.pubKey,pk)
    }
    this.nodes.put(nodeName,v)
  }

  def setPublicKey(nodeName:String,pubKeyOfFault:String,pubKeyOfDoubleFault:String,pubKeyOfEncrypt:String):Unit={
    var pubKey = new publicKeysOfNode(pubKeyOfFault,pubKeyOfDoubleFault,pubKeyOfEncrypt)
    var v : NodeInfo = null
    if(this.nodes.contains(nodeName)){
      v = this.nodes.getOrElse(nodeName,null)
    }
    if(v == null){
      v = new NodeInfo(nodeName,"",pubKey,null)
    }else{
      v = new NodeInfo(nodeName,v.nodeAddress,pubKey,v.priKey)
    }
    this.nodes.put(nodeName,v)
  }

  def removeNode(nodeName:String):Unit={
    this.nodes.remove(nodeName)
  }

  def getNodeInfo(nodeName:String):NodeInfo={
    this.nodes.getOrElse(nodeName,null)
  }

  def getAllNodeNames:Array[String]={
    this.nodes.keys.toArray
  }

  def getAllNodeInfo:Array[NodeInfo]={
    this.nodes.values.toArray
  }

  def loadNodeInfo(addr:String)={
    //val addr = "akka://Repchain@127.0.0.1:22522"
    for(i <- 0 to 3) {
      this.addNode("nodeName"+i,addr)
    }
  }

}
