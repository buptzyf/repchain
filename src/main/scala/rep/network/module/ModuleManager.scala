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

package rep.network.module

import akka.actor.{ ActorRef, Props }
import com.typesafe.config.{ Config }
import rep.app.conf.SystemProfile.Trans_Create_Type_Enum
import rep.app.conf.{ SystemProfile, TimePolicy }
import rep.network.PeerHelper
import rep.network.base.ModuleBase
import rep.network.cache.TransactionPool
import rep.network.persistence.Storager
import rep.ui.web.EventServer
import rep.network.cluster.MemberListener
import rep.network.sync.{ SynchronizeResponser, SynchronizeRequester4Future }
import rep.sc.TransactionDispatcher
import rep.network.consensus.block.{ GenesisBlocker, ConfirmOfBlock, EndorseCollector, Blocker }
import rep.network.consensus.endorse.{ Endorser4Future, DispatchOfRecvEndorsement }
import rep.network.consensus.transaction.{ DispatchOfPreload, PreloaderForTransaction }
import rep.network.consensus.vote.Voter

import rep.storage.ImpDataAccess
import rep.utils.ActorUtils
import rep.utils.GlobalUtils.ActorType
import rep.crypto.cert.SignTool
import rep.log.RepLogger
import rep.storage.verify.verify4Storage
import rep.log.RepTimeTracer

/**
 * Created by shidianyue on 2017/9/22.
 */
object ModuleManager {
  def props(name: String, sysTag: String, enableStatistic: Boolean, enableWebSocket: Boolean, isStartup: Boolean): Props = Props(classOf[ModuleManager], name, sysTag, enableStatistic: Boolean, enableWebSocket: Boolean, isStartup: Boolean)

  case object startup_Consensus
}

class ModuleManager(moduleName: String, sysTag: String, enableStatistic: Boolean, enableWebSocket: Boolean, isStartup: Boolean) extends ModuleBase(moduleName) {

  private val conf = context.system.settings.config

  init()

  if (!checkSystemStorage) {
    context.system.terminate()
  }

  loadModule

  def checkSystemStorage: Boolean = {
    var r = true
    try {
      if (!verify4Storage.verify(sysTag)) {
        r = false
      }
    } catch {
      case e: Exception => {
        r = false
        //throw new Exception("Storager Verify Error,info:" + e.getMessage)
      }
    }
    r
  }

  def init(): Unit = {
    val (ip, port) = ActorUtils.getIpAndPort(selfAddr)
    pe.setIpAndPort(ip, port)
    pe.setSysTag(sysTag)
    val confHeler = new ConfigerHelper(conf, sysTag, pe.getSysTag)
    confHeler.init()
  }

  def loadModule = {
    loadApiModule
    loadSystemModule
    loadConsensusModule
    loadClusterModule

    RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"ModuleManager ${sysTag} start"))
  }

  def loadConsensusModule = {
    val typeConsensus = context.system.settings.config.getString("system.consensus.type")
    if (typeConsensus == "CRFD") {
      context.actorOf(Blocker.props("blocker"), "blocker")
      context.actorOf(GenesisBlocker.props("gensisblock"), "gensisblock")
      context.actorOf(ConfirmOfBlock.props("confirmerofblock"), "confirmerofblock")

      if (SystemProfile.getHasSecondConsensus) {
        context.actorOf(EndorseCollector.props("endorsementcollectioner"), "endorsementcollectioner")
        context.actorOf(DispatchOfRecvEndorsement.props("dispatchofRecvendorsement"), "dispatchofRecvendorsement")
      }

      if (this.isStartup) {
        context.actorOf(TransactionDispatcher.props("transactiondispatcher"), "transactiondispatcher")
      }
      context.actorOf(DispatchOfPreload.props("dispatchofpreload"), "dispatchofpreload")

      context.actorOf(Voter.props("voter"), "voter")
      context.actorOf(TransactionPool.props("transactionpool"), "transactionpool")
    }
  }

  def loadApiModule = {
    //if (enableStatistic) context.actorOf(Props[StatisticCollection], "statistic")
    if (enableStatistic) RepTimeTracer.openTimeTrace else RepTimeTracer.closeTimeTrace
    if (enableWebSocket) /*{if(pe.getActorRef(ActorType.webapi) == null) */ context.system.actorOf(Props[EventServer], "webapi")

  }

  def loadSystemModule = {
    context.actorOf(Storager.props("storager"), "storager")
    context.actorOf(SynchronizeRequester4Future.props("synchrequester"), "synchrequester")
    context.actorOf(SynchronizeResponser.props("synchresponser"), "synchresponser")
  }

  def loadClusterModule = {
    context.actorOf(MemberListener.props("memberlistener"), "memberlistener")
  }

  //除了广播消息，P2P的跨域消息都通过其中转（同步，存储等）
  override def receive: Receive = {
    case ModuleManager.startup_Consensus =>
      RepLogger.trace(RepLogger.System_Logger, this.getLogMsgPrefix(s"trans create type ${SystemProfile.getTransCreateType},actor=${pe.getActorRef(ActorType.peerhelper)}"))
      if (SystemProfile.getTransCreateType == Trans_Create_Type_Enum.AUTO && pe.getActorRef(ActorType.peerhelper) == null) {
        context.actorOf(PeerHelper.props("peerhelper"), "peerhelper")
      }
      if (pe.getActorRef(ActorType.voter) == null) {
        context.actorOf(Voter.props("voter"), "voter")
      }
      pe.getActorRef(ActorType.voter) ! Voter.VoteOfBlocker
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
   * @param jksFilePath
   * @param pwd
   * @param trustJksFilePath
   * @param trustPwd
   */
  private def authInit(sysTag: String, jksFilePath: String, pwd: String, trustJksFilePath: String, trustPwd: String): Unit = {
    //init the ECDSA param
    SignTool.loadPrivateKey(sysTag, pwd, jksFilePath)
    SignTool.loadNodeCertList(trustPwd, trustJksFilePath)
    //ECDSASign.apply(sysTag, jksFilePath, pwd, trustJksFilePath, trustPwd)
    //ECDSASign.preLoadKey(sysTag)
  }

  /**
   * 根据配置初始化本地安全配置
   */
  private def authInitByCfg(sysTag: String): Unit = {
    val mykeyPath = conf.getString("akka.remote.netty.ssl.security.base-path") + sysTag + ".jks"
    val psw = conf.getString("akka.remote.netty.ssl.security.key-store-password")
    val trustPath = conf.getString("akka.remote.netty.ssl.security.trust-store")
    val trustPwd = conf.getString("akka.remote.netty.ssl.security.trust-store-password")
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
