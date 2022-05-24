/*
 * Copyright  2019 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
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
 *
 */

package rep.network.tools

import akka.actor.{ActorRef, ActorSystem, Address, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import java.util.concurrent.atomic._

import rep.utils.GlobalUtils.{BlockerInfo, NodeStatus}
import rep.network.persistence.BlockCache
import rep.network.tools.transpool.PoolOfTransaction
import java.util.concurrent.ConcurrentHashMap

import javax.net.ssl.SSLContext
import rep.app.system.RepChainSystemContext
import rep.network.consensus.cfrd.endorse.RecvEndorsInfo

import scala.collection.JavaConverters._
import rep.network.sync.SyncMsg.MaxBlockInfo
import rep.proto.rc2.{Block, BlockchainInfo, Transaction}

/**
 * Peer business logic node stared space（based on system）
 */
/**
 * @author jiangbuyun
 * @version	0.7
 * @update 2018-05 jiangbuyun
 */
class PeerExtensionImpl extends Extension {

  private var ctx : RepChainSystemContext= null

  def getRepChainContext:RepChainSystemContext={
    this.ctx
  }

  def setRepChainContext(ctx:RepChainSystemContext):Unit={
    this.ctx = ctx
  }

  def getSysTag = ctx.getSystemName
/*********交易池缓存管理开始************/


/*********交易池缓存管理结束************/

/*********区块缓存管理开始************/
  private val blockCache: BlockCache = new BlockCache

  def getBlockCacheMgr: BlockCache = {
    this.blockCache
  }
/*********区块缓存管理结束************/

/*********组网节点信息管理，包括抽签候选人信息开始************/
  private val nodemgr = new NodeMgr

  def getNodeMgr: NodeMgr = {
    this.nodemgr
  }
/*********组网节点信息管理，包括抽签候选人信息结束************/
private var startVoteInfo:AtomicReference[MaxBlockInfo] = new AtomicReference[MaxBlockInfo](new MaxBlockInfo(0,""))

def setStartVoteInfo(value:MaxBlockInfo)={
  this.startVoteInfo.set(value)
}

def getStartVoteInfo:MaxBlockInfo={
  this.startVoteInfo.get
}

private var createBlockHeight : AtomicLong = new AtomicLong(0)

def setCreateHeight(value:Long)={
  this.createBlockHeight.set(value)
}

def getCreateHeight:Long={
  this.createBlockHeight.get
}
  
private var confirmBlockHeight : AtomicLong = new AtomicLong(0)

def setConfirmHeight(value:Long)={
  this.confirmBlockHeight.set(value)
}

def getConfirmHeight:Long={
  this.confirmBlockHeight.get
}

def getMaxHeight4SimpleRaft:Long={
  scala.math.max(scala.math.max(this.getConfirmHeight, this.getConfirmHeight),this.getCurrentHeight)
}

private var timeoutOfRaft:AtomicLong = new AtomicLong(0)

  def  resetTimeoutOfRaft ={
  this.timeoutOfRaft.set(System.currentTimeMillis())
}

 def getTimeoutOfRaft:Long = {
  this.timeoutOfRaft.get()
}

  private var zeroOfTransNumFlag:AtomicBoolean = new AtomicBoolean(false)

 def setZeroOfTransNumFlag(value:Boolean)={
  this.zeroOfTransNumFlag.set(value)
}

   def getZeroOfTransNumFlag:Boolean={
    this.zeroOfTransNumFlag.get()
  }

/*********节点当前链信息开始************/
  private var SystemCurrentChainInfo: AtomicReference[BlockchainInfo] =
    new AtomicReference[BlockchainInfo](new BlockchainInfo(0l, 0l, _root_.com.google.protobuf.ByteString.EMPTY, _root_.com.google.protobuf.ByteString.EMPTY))

  def getCurrentBlockHash: String = {
    this.SystemCurrentChainInfo.get.currentBlockHash.toStringUtf8()
  }

  def getCurrentHeight = this.SystemCurrentChainInfo.get.height

  def resetSystemCurrentChainStatus(value: BlockchainInfo) {
    this.SystemCurrentChainInfo.set(value)
  }

  def getSystemCurrentChainStatus: BlockchainInfo = {
    this.SystemCurrentChainInfo.get
  }
/*********节点当前链信息结束************/

/*********出块人开始************/
  private var blocker: AtomicReference[BlockerInfo] = new AtomicReference[BlockerInfo](new BlockerInfo("", -1, 0l,"",-1))

  def resetBlocker(blker: BlockerInfo): Unit = {
    blocker.set(blker)
  }

  def getBlocker = blocker.get
/*********出块人结束************/

/*********节点状态开始************/
  private var synching: AtomicBoolean = new AtomicBoolean(false)

  def setSynching(status:Boolean) = this.synching.set(status)
  

  def isSynching = this.synching.get
/*********节点状态结束************/

/*********节点信息相关操作开始************/



/*********节点信息相关操作结束************/

/*********系统Actor注册相关操作开始************/
  private implicit var actorList = new ConcurrentHashMap[Int, ActorRef] asScala

  def register(actorName: Int, actorRef: ActorRef) = {
    actorList.put(actorName, actorRef)
  }

  def getActorRef(actorName: Int): ActorRef = {
    var r: ActorRef = null
    if(actorList.contains(actorName)){
      r = actorList(actorName)
    }
    
    r
  }

  /*def unregister(actorName: Int) = {
    actorList -= actorName
  }*/
  
/*********系统Actor注册相关操作结束************/

  private var CurrentEndorseInfo = new RecvEndorsInfo()

  def getCurrentEndorseInfo:RecvEndorsInfo={
    this.CurrentEndorseInfo
  }

  private implicit var preloadTransOfWaiting = new ConcurrentHashMap[String,Seq[Transaction]]() asScala

  def addTrans(identifierOfTrans:String,trans:Seq[Transaction]):Unit={
    this.preloadTransOfWaiting.put(identifierOfTrans,trans)
  }

  def getTrans(identifierOfTrans:String):Seq[Transaction]={
    this.preloadTransOfWaiting.getOrElse(identifierOfTrans,null)
  }

  def removeTrans(identifierOfTrans: String): Unit ={
    this.preloadTransOfWaiting.remove(identifierOfTrans)
  }

  private implicit var preloadBlockOfWaiting = new ConcurrentHashMap[String,Block]() asScala

  def addBlock(identifierOfBlock:String,block:Block):Unit={
    this.preloadBlockOfWaiting.put(identifierOfBlock,block)
  }

  def getBlock(identifierOfBlock:String):Block={
    this.preloadBlockOfWaiting.getOrElse(identifierOfBlock,null)
  }

  def removeBlock(identifierOfBlock: String): Unit ={
    this.preloadBlockOfWaiting.remove(identifierOfBlock)
  }

  private var sslContext : SSLContext = null
  def getSSLContext:SSLContext={
    sslContext
  }

  def setSSLContext(value: SSLContext)={
    sslContext = value
  }
}

object PeerExtension
  extends ExtensionId[PeerExtensionImpl]
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