/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Fintech Research Center of ISCAS.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BA SIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rep.network.tools

import akka.actor.{ActorSystem, Address, ExtendedActorSystem,ActorRef, Extension, ExtensionId, ExtensionIdProvider}
import rep.protos.peer.{Transaction}
import scala.collection.immutable
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import java.util.concurrent.atomic._
import org.bouncycastle.asn1.cmp.ProtectedPart
import java.util.concurrent.locks._
import com.google.protobuf.UInt32Value
import rep.network.consensus.block.BlockModule._
import rep.protos.peer._
import java.util.concurrent.ConcurrentLinkedQueue
import rep.utils.GlobalUtils.{TranscationPoolPackage,BlockChainStatus}
import scala.collection.script.Remove



/**
  * Peer business logic node stared space（based on system）
  */
/**
  * @author shidianyue
  * @version	0.7
  * @update 2018-05 jiangbuyun
  * */
class PeerExtensionImpl extends Extension {

  private val transactions = mutable.LinkedHashMap.empty[ String, TranscationPoolPackage ]
  
  //本地缓存网络节点
  private var nodes : immutable.TreeMap[String,Address] = new immutable.TreeMap[String,Address]() 
  //本地缓存稳定的网络节点
  private var stableNodes : immutable.TreeMap[String,Address] = new immutable.TreeMap[String,Address]() 
  //本地上次候选人名单
  private var candidator : immutable.TreeMap[String,String] = new immutable.TreeMap[String,String]() 
  
  private var tmpEndorse:ConcurrentLinkedQueue[PrimaryBlock4Cache]  = new ConcurrentLinkedQueue[PrimaryBlock4Cache]();  
  
  private var blocker:AtomicReference[String] = new AtomicReference[String]("")
  //保存当前hash值（通过全网广播同步更新成最新的block hash）
  
  private var voteBlockHash:AtomicReference[String] = new AtomicReference[String]("0")
  private var SystemCurrentChainStatus:AtomicReference[BlockChainStatus] = new AtomicReference[BlockChainStatus](new BlockChainStatus("0","",0))
 
  //必须是线程安全的,需要原子操作
  private var isBlocking:AtomicBoolean = new AtomicBoolean(false)
  private var endorState:AtomicBoolean = new AtomicBoolean(false)
  private var isBlockVote:AtomicBoolean = new AtomicBoolean(false)
  
  private var isSync:AtomicBoolean = new AtomicBoolean(false)
  private var cacheBlkNum:AtomicInteger = new AtomicInteger(0)
  private var seedNode:AtomicReference[Address] = new AtomicReference[Address](Address("", ""))
  private var blker_index:AtomicInteger = new AtomicInteger(1)
  
  private var sys_ip:AtomicReference[String] = new AtomicReference[String]("")
  private var sys_port:AtomicReference[String] = new AtomicReference[String]("")
  private var dbTag:AtomicReference[String] = new AtomicReference[String]("")
  private var sysTag:AtomicReference[String] = new AtomicReference[String]("")
  
  //数据访问锁
  private val  transLock : Lock = new ReentrantLock();
  private val  nodesLock : Lock = new ReentrantLock();
  private val  nodesStableLock : Lock = new ReentrantLock();
  private val  candidatorLock : Lock = new ReentrantLock();
  
  //系统状态
  //0 系统启动；1 组网中；2 组网完成； 3 同步； 4 同步完成；5 投票；6 投票完成； 7 背书； 8 背书完成； 9 出块； 10 出块完成 11 创始块开始 12 创始块结束
  private var systemStatus:AtomicInteger = new AtomicInteger(0)
  
  def getTmpEndorse:PrimaryBlock4Cache={
    this.tmpEndorse.poll()
  }
  
  def setTmpEndorse(v:PrimaryBlock4Cache):Unit={
    this.tmpEndorse.add(v)
  }
  
  def getTransListClone(num: Int): Seq[ TranscationPoolPackage ] = {
    val result = ArrayBuffer.empty[ TranscationPoolPackage ]
    transLock.lock()
    try{
      val len = if (transactions.size < num) transactions.size else num
      transactions.take(len).foreach(pair => pair._2 +=: result)
    }finally{
      transLock.unlock()
    }
    result.reverse
  }

  def putTran(tran: Transaction): Unit = {
    transLock.lock()
    try{
      if (transactions.contains(tran.txid)) {
        println(s"${tran.txid} exists in cache")
      }
      else transactions.put(tran.txid, new TranscationPoolPackage(tran,System.currentTimeMillis()/1000))
    }finally {
      transLock.unlock()
    }
  }
  
  def findTrans(txid:String):Boolean = {
    var b :Boolean = false
    if(transactions.contains(txid)){
        b = true
    }
    b
  }

  //TODO kami 需要清空之前所有的block的Transaction
  def removeTrans(trans: Seq[ Transaction ]): Unit = {
    transLock.lock()
    try{
      for (curT <- trans) {
          if (transactions.contains(curT.txid)) transactions.remove(curT.txid)
      }
    }finally{
        transLock.unlock()
    }
  }
  
  def removeTranscation(tran:Transaction):Unit={
    transLock.lock()
    try{
       if (transactions.contains(tran.txid)) transactions.remove(tran.txid)
    }finally{
        transLock.unlock()
    }
  }

  def getTransLength() : Int = {
    var len = 0
    transLock.lock()
    try{
      len = transactions.size
    }finally{
      transLock.unlock()
    }
    len
  }

  
  def getCurrentBlockHash : String = {
    this.getSystemCurrentChainStatus.CurrentBlockHash
  }

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
      nodes = immutable.TreeMap.empty[String,Address]
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
      stableNodes = immutable.TreeMap.empty[String,Address]
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
   
  def putCandidator(addr: String): Unit = {
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
      candidator = immutable.TreeMap.empty[String,String]
    }finally{
      candidatorLock.unlock()
    }
    nds.foreach(addr=>{
      putCandidator(addr)
    })
  }

  

  def resetBlocker(addr: String): Unit = {
    blocker.set(addr)
  }

  def getBlocker = blocker.get

  def resetSeedNode(addr: Address): Unit = {
    seedNode.set(addr)
  }

  def getSeedNode = seedNode.get
  
  def resetSystemCurrentChainStatus(value:BlockChainStatus){
    this.SystemCurrentChainStatus.set(value)
  }
  
  def getSystemCurrentChainStatus:BlockChainStatus={
    this.SystemCurrentChainStatus.get
  }
  
  def setIpAndPort(ip: String, port: String): Unit = {
    this.sys_ip.set(ip)
    this.sys_port.set(port)
  }

  def getIp = sys_ip.get

  def getPort = sys_port.get

  def setDBTag(root: String) = dbTag.set(root)

  def getDBTag = dbTag.get

  def setSystemStatus(status: Int) = systemStatus.set(status)

  def getSystemStatus = systemStatus.get
  
  def getMerk = this.getSystemCurrentChainStatus.CurrentMerkle

  def setSysTag(name: String) = sysTag.set(name)

  def getSysTag = sysTag.get

  def setIsSync(isSync: Boolean) = this.isSync.set(isSync)

  def getIsSync() = isSync.get
  
  def setIsBlocking(isblocking:Boolean) = this.isBlocking.set(isblocking)
  
  def getIsBlocking() = this.isBlocking.get
  
  def getIsBlockVote() = this.isBlockVote.get
  
  def setIsBlockVote(value:Boolean) = this.isBlockVote.set(value)
  
  def setEndorState(endorState:Boolean) = this.endorState.set(endorState)
  
  def getEndorState() = this.endorState.get

  def getCacheHeight() = this.getSystemCurrentChainStatus.CurrentHeight

  def addCacheBlkNum() = cacheBlkNum.addAndGet(1)

  def rmCacheBlkNum() = cacheBlkNum.addAndGet(-1)

  def getCacheBlkNum() = cacheBlkNum.get
  
  def getBlker_index = blker_index.get
  def AddBlker_index = blker_index.getAndAdd(1)
  def resetBlker_index = blker_index.set(0)
  def setBlker_index(value:Int) = blker_index.set(value)
  
  def getVoteBlockHash = voteBlockHash.get
  def setVoteBlockHash(value:String) = voteBlockHash.set(value)
  
  /*********系统Actor注册相关操作开始************/
  private val actorList = mutable.HashMap[Int, ActorRef]()

  def register(actorName:Int, actorRef: ActorRef)={
    actorList.put(actorName,actorRef)
  }

  def getActorRef(actorName:Int) = actorList.get(actorName)

  def unregister(actorName:Int) = actorList.remove(actorName)
  /*********系统Actor注册相关操作结束************/
}

object PeerExtension
  extends ExtensionId[ PeerExtensionImpl ]
    with ExtensionIdProvider {
  //The lookup method is required by ExtensionIdProvider,
  // so we return ourselves here, this allows us
  // to configure our extension to be loaded when
  // the ActorSystem starts up
  override def lookup = PeerExtension

  //This method will be called by Akka
  // to instantiate our Extension
  override def createExtension(system: ExtendedActorSystem) = new PeerExtensionImpl

  /**
    * Java API: retrieve the Count extension for the given system.
    */
  override def get(system: ActorSystem): PeerExtensionImpl = super.get(system)
}