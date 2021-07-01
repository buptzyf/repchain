package rep.network.consensus.asyncconsensus.config

import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._

class ConfigOfManager private (val nodeNames:Array[String],val nodeAddrs:Array[String]) {
  private  var caches = new ConcurrentHashMap[String, ConfigOfDumbo] asScala
  private  var nodeList = new ConcurrentHashMap[String, String] asScala

  loadNodeInfos

  private def loadNodeInfos={
    var i = 0
    for(i <- 0 to this.nodeNames.length-1){
      val name = this.nodeNames(i)
      val addr = this.nodeAddrs(i)
      val config = new ConfigOfDumbo(name)
      caches += name -> config
      this.nodeList += name -> addr
    }
  }

  def getConfig(name:String):ConfigOfDumbo={
    if(this.caches.contains(name)){
      this.caches(name)
    }else{
      null
    }
  }

  def getNodeCount:Int={
    this.nodeNames.length
  }

  def getNodeNames:Array[String] ={
    this.nodeNames
  }

  def getNodeNameAndAddr:Array[(String,String)]={
    this.nodeList.toArray
  }

}

object ConfigOfManager{
  private var mgr : ConfigOfManager = null

  def loadManager(nodeNames:Array[String],nodeAddrs:Array[String]):ConfigOfManager={
    var tmp : ConfigOfManager = null
    synchronized {
      if(mgr == null){
        if(nodeNames != null){
          mgr = new ConfigOfManager(nodeNames,nodeAddrs)
          tmp = mgr
        }
      }else{
        tmp = mgr
      }
      tmp
    }
  }

  def getManager:ConfigOfManager={
    loadManager(null,null)
  }
}
