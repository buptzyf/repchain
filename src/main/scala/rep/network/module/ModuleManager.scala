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

import akka.actor.{ ActorRef, Props }
import com.typesafe.config.{ Config }
import rep.app.conf.SystemProfile.Trans_Create_Type_Enum
import rep.app.conf.{ SystemProfile, TimePolicy }
import rep.network.PeerHelper
import rep.network.base.ModuleBase
import rep.network.cache.TransactionPool
import rep.network.persistence.Storager
import rep.network.tools.Statistic.StatisticCollection
import rep.ui.web.EventServer
import rep.network.cluster.MemberListener
import rep.network.sync.{ SynchronizeResponser, SynchronizeRequester }

import rep.network.consensus.block.{ GenesisBlocker, ConfirmOfBlock, EndorseCollector, Blocker }
import rep.network.consensus.endorse.Endorser
import rep.network.consensus.transaction.PreloaderForTransaction
import rep.network.consensus.vote.Voter
import rep.sc.TransProcessor

import rep.storage.ImpDataAccess
import rep.utils.ActorUtils
import rep.utils.GlobalUtils.ActorType
import rep.log.trace.LogType
import rep.crypto.cert.SignTool

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
  loadModule

  def init(): Unit = {
    val (ip, port) = ActorUtils.getIpAndPort(selfAddr)
    pe.setIpAndPort(ip, port)
    pe.setSysTag(sysTag)
    val confHeler = new ConfigerHelper(conf, sysTag, pe.getSysTag)
    confHeler.init()
  }

  def loadModule = {
    loadClusterModule
    loadApiModule
    loadSystemModule
    loadConsensusModule

    /*if (isStartup) {
      try{
        Thread.sleep(2000)
      }catch{
        case _ => logMsg(LogType.INFO, moduleName + "~" + s"ModuleManager ${sysTag} delay startup error")
      }
      }*/
     

    logMsg(LogType.INFO, moduleName + "~" + s"ModuleManager ${sysTag} start")
  }

  def loadConsensusModule = {
    val typeConsensus = context.system.settings.config.getString("system.consensus.type")
    if (typeConsensus == "CRFD") {
      context.actorOf(Blocker.props("blocker"), "blocker")
      context.actorOf(GenesisBlocker.props("gensisblock"), "gensisblock")
      context.actorOf(ConfirmOfBlock.props("confirmerofblock"), "confirmerofblock")
      context.actorOf(EndorseCollector.props("endorsementcollectioner"), "endorsementcollectioner")
      context.actorOf(Endorser.props("endorser"), "endorser")
      context.actorOf(PreloaderForTransaction.props("preloaderoftransaction", context.actorOf(TransProcessor.props("sandbox_for_Preload", self), "sandboxProcessor")), "preloaderoftransaction")
      //context.actorOf(Endorser.props("endorser"), "endorser")
      context.actorOf(Voter.props("voter"), "voter")
      context.actorOf(TransactionPool.props("transactionpool"), "transactionpool")
    }
  }

  def loadApiModule = {
    if (enableStatistic) context.actorOf(Props[StatisticCollection], "statistic")
    if (enableWebSocket) context.system.actorOf(Props[EventServer], "webapi")

  }

  def loadSystemModule = {
    context.actorOf(Storager.props("storager"), "storager")
    context.actorOf(SynchronizeRequester.props("synchrequester"), "synchrequester")
    context.actorOf(SynchronizeResponser.props("synchresponser"), "synchresponser")
  }

  def loadClusterModule = {
    context.actorOf(MemberListener.props("memberlistener"), "memberlistener")
  }

  //除了广播消息，P2P的跨域消息都通过其中转（同步，存储等）
  override def receive: Receive = {
    case ModuleManager.startup_Consensus => 
      if (SystemProfile.getTransCreateType == Trans_Create_Type_Enum.AUTO) {
        context.actorOf(PeerHelper.props("peerhelper"), "peerhelper")
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
