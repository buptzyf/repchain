package rep.network.tools

import scala.collection.immutable.{ TreeMap }
import akka.actor.{ Address }
import java.util.concurrent.locks._

class NodeMgr {
  private val nodesLock: Lock = new ReentrantLock();
  private val nodesStableLock: Lock = new ReentrantLock();
  private val candidatorLock: Lock = new ReentrantLock();
  //本地缓存网络节点
  private var nodes: TreeMap[String, Address] = new TreeMap[String, Address]()
  //本地缓存稳定的网络节点
  private var stableNodes: TreeMap[String, Address] = new TreeMap[String, Address]()
  //本地上次候选人名单
  private var candidator: TreeMap[String, String] = new TreeMap[String, String]()

  
  
  def getNodes :Set[Address] = {
    var source = Set.empty[Address]
    nodesLock.lock()
    try{
      source = nodes.values.toArray.toSet
    }finally{
      nodesLock.unlock()
    }
    source
  }

  def putNode(addr: Address): Unit = {
    nodesLock.lock()
    try{
      val key = addr.toString
      nodes += key -> addr
    }finally{
      nodesLock.unlock()
    }
  }

  def removeNode(addr: Address): Unit = {
    nodesLock.lock()
    try{
      val key = addr.toString
      nodes -= key 
    }finally{
      nodesLock.unlock()
    }
  }

  def resetNodes(nds: Set[ Address ]): Unit = {
    nodesLock.lock()
    try{
      nodes = TreeMap.empty[String,Address]
    }finally{
      nodesLock.unlock()
    }
    nds.foreach(addr=>{
      putNode(addr)
    })
  }
  
  def getStableNodes :Set[Address] = {
    var source = Set.empty[Address]
    nodesStableLock.lock()
    try{
      source = stableNodes.values.toArray.toSet
    }finally{
      nodesStableLock.unlock()
    }
    source
  }

  def putStableNode(addr: Address): Unit = {
    nodesStableLock.lock()
    try{
      val key = addr.toString
      stableNodes += key -> addr
    }finally{
      nodesStableLock.unlock()
    }
  }

  def removeStableNode(addr: Address): Unit = {
    nodesStableLock.lock()
    try{
      val key = addr.toString
      stableNodes -= key 
    }finally{
      nodesStableLock.unlock()
    }
  }

  def resetStableNodes(nds: Set[ Address ]): Unit = {
    nodesStableLock.lock()
    try{
      stableNodes = TreeMap.empty[String,Address]
    }finally{
      nodesStableLock.unlock()
    }
    nds.foreach(addr=>{
      putStableNode(addr)
    })
  }
  
  def getCandidator :Set[String] = {
      var source = Set.empty[String]
      candidatorLock.lock()
      try{
        source = candidator.values.toArray.toSet
      }finally{
        candidatorLock.unlock()
      }
      source
    }
   
  private def putCandidator(addr: String): Unit = {
    candidatorLock.lock()
    try{
      val key = addr.toString
      candidator += key -> addr
    }finally{
      candidatorLock.unlock()
    }
  }

  def resetCandidator(nds: Array[ String ]): Unit = {
    candidatorLock.lock()
    try{
      candidator = TreeMap.empty[String,String]
    }finally{
      candidatorLock.unlock()
    }
    nds.foreach(addr=>{
      putCandidator(addr)
    })
  }

}