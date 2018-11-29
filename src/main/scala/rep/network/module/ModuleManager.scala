/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
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

package rep.network.module

import akka.actor.{ActorRef, Props}
import com.typesafe.config.{Config}
import rep.app.conf.SystemProfile.Trans_Create_Type_Enum
import rep.app.conf.{SystemProfile, TimePolicy}
import rep.crypto.ECDSASign
import rep.network.PeerHelper
import rep.network.base.ModuleBase
import rep.network.cache.TransactionPool
import rep.network.consensus.CRFD.{ConsensusInitFinish}
import rep.network.consensus.ConsensusManager
import rep.network.module.ModuleManager.{ClusterJoined, TargetBlock}
import rep.network.persistence.PersistenceModule
import rep.network.sync.SyncModule
import rep.network.sync.SyncModule.{ChainDataReqSingleBlk, SetupSync}
import rep.storage.ImpDataAccess
import rep.utils.ActorUtils
import rep.utils.GlobalUtils.ActorType
import rep.log.trace.LogType

/**
  * Created by shidianyue on 2017/9/22.
  */
object ModuleManager {
  def props(name: String, sysTag: String): Props = Props(classOf[ ModuleManager ], name, sysTag)
  
  case class TargetBlock(height: Long, blker: ActorRef)
  

  case object ClusterJoined

}

class ModuleManager(moduleName: String, sysTag: String) extends ModuleBase(moduleName) {

  private val conf = context.system.settings.config
  private var persistence: ActorRef = null
  private var sync: ActorRef = null
  private var transactionPool: ActorRef = null
  private var consensus: ActorRef = null
  private var transCreator: ActorRef = null

  private var isConsensusFinished = false
  private var isClusterJoined = false

  init()

  loadModule()

  def init(): Unit = {
    //Get IP and port
    val (ip, port) = ActorUtils.getIpAndPort(selfAddr)
    pe.setIpAndPort(ip, port)
    pe.setDBTag(sysTag)
    pe.setSysTag(sysTag)
    val confHeler = new ConfigerHelper(conf, sysTag, pe.getDBTag)
    confHeler.init()
    
    
    
    pe.setIsSync(true)
    registerActorRef(ActorType.MODULE_MANAGER, self) //register itself
  }

  
  
  def loadModule() = {
    persistence = context.actorOf(PersistenceModule.props("persistence"), "persistence")
    sync = context.actorOf(SyncModule.props("sync"), "sync")
    transactionPool = context.actorOf(TransactionPool.props("transactionPool"), "transactionPool")
    consensus = context.actorOf(ConsensusManager.props("consensusManager", context.system.settings.config), "consensusManager")

    registerActorRef(ActorType.PERSISTENCE_MODULE, persistence)
    registerActorRef(ActorType.SYNC_MODULE, sync)
    registerActorRef(ActorType.TRANSACTION_POOL, transactionPool)
    registerActorRef(ActorType.CONSENSUS_MANAGER, consensus)

    logMsg(LogType.INFO, moduleName+"~"+ s"ModuleManager ${sysTag} start")

    SystemProfile.getTransCreateType match {
      case Trans_Create_Type_Enum.AUTO =>
        transCreator = context.actorOf(PeerHelper.props("helper"), "helper")
      case Trans_Create_Type_Enum.MANUAL => // ignore
    }
  }

  def syncStartCheck = {
    (isClusterJoined && isConsensusFinished) match {
      case true =>
        getActorRef(ActorType.SYNC_MODULE) ! SetupSync
        logMsg(LogType.INFO, "Sync Start Ticket")
      case false => // ignore
    }
  }


  //除了广播消息，P2P的跨域消息都通过其中转（同步，存储等）
  override def receive: Receive = {

    case tb: TargetBlock =>
      //存储向出块人节点的同步模块请求同步数据
      tb.blker ! ChainDataReqSingleBlk(sync,
        tb.height)
    case chaindataReqSB: ChainDataReqSingleBlk =>
      //转移同步请求至同步模块
      sync ! chaindataReqSB

    case ClusterJoined =>
      isClusterJoined = true
      syncStartCheck

    case ConsensusInitFinish =>
      isConsensusFinished = true
      syncStartCheck

    case _ => //ignore
  }
}


class ConfigerHelper(conf: Config, tag: String, dbTag: String) {

  def init(): Unit = {
    authInitByCfg(tag)
    dbInit(dbTag)
    //sysInit(conf)
    timePolicyInit(conf)
  }

  /**
    * Authorization module init
    *
    * @param storeFilePath
    * @param pwd
    * @param trustStoreFilePath
    * @param trustPwd
    */
  private def authInit(sysTag: String, storeFilePath: String, pwd: String, trustStoreFilePath: String, trustPwd: String): Unit = {
    //init the ECDSA param
    ECDSASign.apply(sysTag, storeFilePath, pwd, trustStoreFilePath, trustPwd)
    ECDSASign.preLoadKey(sysTag)
  }

  /**
    * 根据配置初始化本地安全配置
    */
  private def authInitByCfg(sysTag: String): Unit = {
    val store = SystemProfile.getKeyStore
    var mykeyPath = ""
    val psw = conf.getString("akka.remote.netty.ssl.security.key-store-password")
    var trustPath = ""
    val trustPwd = conf.getString("akka.remote.netty.ssl.security.trust-store-password")
    if (store.equalsIgnoreCase("pfx")) {
      mykeyPath = conf.getString("akka.remote.netty.ssl.security.base-path") + "mykeystore_" + sysTag + ".pfx"
      trustPath = conf.getString("akka.remote.netty.ssl.security.base-path") + "mytruststore" + ".pfx"
    } else if (store.equalsIgnoreCase("jks")) {
      mykeyPath = conf.getString("akka.remote.netty.ssl.security.base-path") + "mykeystore_" + sysTag + ".jks"
      trustPath = conf.getString("akka.remote.netty.ssl.security.trust-store")
    } else {
      throw new Exception("目前不支持此存储文件")
    }

    authInit(sysTag, mykeyPath, psw, trustPath, trustPwd)
  }

  /**
    * 初始化DB信息
    *
    * @param dbTag
    */
  private def dbInit(dbTag: String): Unit = {
    ImpDataAccess.GetDataAccess(dbTag)

  }

  /**
    * 初始化系统相关配置
    *
    * @param config
    */
  private def sysInit(config: Config): Unit = {
    SystemProfile.initConfigSystem(config)
  }

  /**
    * 初始化时间策略配置
    *
    * @param config
    */
  private def timePolicyInit(config: Config): Unit = {
    TimePolicy.initTimePolicy(config)
  }

}
