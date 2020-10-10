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
import rep.protos.peer.{BlockchainInfo, Transaction}
import java.util.concurrent.atomic._

import rep.utils.GlobalUtils.{BlockerInfo, NodeStatus}
import rep.network.persistence.BlockCache
import rep.network.tools.transpool.TransactionPoolMgr
import java.util.concurrent.ConcurrentHashMap

import rep.app.conf.SystemProfile
import rep.network.consensus.cfrd.endorse.RecvEndorsInfo
//import scala.jdk.CollectionConverters._
import scala.collection.JavaConverters._
import rep.network.sync.SyncMsg.MaxBlockInfo

/**
 * Peer business logic node stared space（based on system）
 */
/**
 * @author jiangbuyun
 * @version	0.7
 * @update 2018-05 jiangbuyun
 */
class PeerExtensionImpl extends Extension {

/*********交易池缓存管理开始************/
  private val transactionmgr = new TransactionPoolMgr

  def getTransPoolMgr: TransactionPoolMgr = {
    this.transactionmgr
  }
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
  private var sys_ip: AtomicReference[String] = new AtomicReference[String]("")

  private var sys_port: AtomicReference[String] = new AtomicReference[String]("")

  private var sysTag: AtomicReference[String] = new AtomicReference[String]("")

  def setIpAndPort(ip: String, port: String): Unit = {
    this.sys_ip.set(ip)
    this.sys_port.set(port)
  }

  def getIp = this.sys_ip.get

  def getPort = this.sys_port.get

  def setSysTag(name: String) = sysTag.set(name)

  def getSysTag = sysTag.get
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