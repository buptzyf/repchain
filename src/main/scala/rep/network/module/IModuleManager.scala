package rep.network.module

import akka.actor.Props
import com.typesafe.config.Config
import rep.app.conf.SystemProfile.Trans_Create_Type_Enum
import rep.app.conf.{SystemProfile, TimePolicy}
import rep.crypto.cert.SignTool
import rep.log.{RepLogger, RepTimeTracer}
import rep.network.autotransaction.PeerHelper
import rep.network.base.ModuleBase
import rep.network.cluster.MemberListener
import rep.network.module.cfrd.CFRDActorType
import rep.network.transaction.DispatchOfPreload
import rep.sc.TransactionDispatcher
import rep.storage.ImpDataAccess
import rep.storage.verify.verify4Storage
import rep.ui.web.EventServer
import rep.utils.ActorUtils
import rep.network.genesis.GenesisBlocker


/**
 * Created by jiangbuyun on 2020/03/15.
 * 模块管理的基础类，启动时加载默认的公共的actor，然后根据不同的共识协议，加载不同的共识actor
 * 不同的协议需要继承这个基础类
 */
object IModuleManager {
  def props(name: String, sysTag: String, enableStatistic: Boolean, enableWebSocket: Boolean, isStartup: Boolean): Props = Props(classOf[IModuleManager], name, sysTag, enableStatistic: Boolean, enableWebSocket: Boolean, isStartup: Boolean)

  //启动共识模块的消息，这个消息的发送前提是系统同步已经完成，消息的发送者为同步模块
  case object startup_Consensus
}

class IModuleManager(moduleName: String, sysTag: String, enableStatistic: Boolean, enableWebSocket: Boolean, isStartup: Boolean) extends ModuleBase(moduleName) with IModule{

  private val conf = context.system.settings.config

  init

  if (!checkSystemStorage) {
    context.system.terminate()
  }


  loadCommonActor
  loadConsensusModule

  private def checkSystemStorage: Boolean = {
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

  //初始化系统actor，完成公共actor的装载，包括证书、配置信息的装载，也包括存储的检查
  private def init: Unit = {
    pe.register(ModuleActorType.ActorType.modulemanager,self)
    val (ip, port) = ActorUtils.getIpAndPort(selfAddr)
    pe.setIpAndPort(ip, port)
    pe.setSysTag(sysTag)
    val confHeler = new ConfigerHelper(conf, sysTag, pe.getSysTag)
    confHeler.init()

  }

  private def loadCommonActor:Unit = {
    loadApiModule
    loadTransModule
    loadGensisModule
    loadClusterModule
  }

  private def loadApiModule = {
    if (enableStatistic) RepTimeTracer.openTimeTrace else RepTimeTracer.closeTimeTrace
    if (enableWebSocket) {
      pe.register(ModuleActorType.ActorType.webapi,context.system.actorOf(Props[EventServer], "webapi"))
    }
  }

  private def loadAutoTestStub:Any = {
    if (SystemProfile.getTransCreateType == Trans_Create_Type_Enum.AUTO ) {
      pe.register(ModuleActorType.ActorType.peerhelper,context.actorOf(PeerHelper.props("peerhelper"), "peerhelper"))
    }
  }

  private def loadTransModule:Any={
    if (this.isStartup) {
      pe.register(ModuleActorType.ActorType.transactiondispatcher, context.actorOf(TransactionDispatcher.props("transactiondispatcher"), "transactiondispatcher"))
    }
    pe.register(ModuleActorType.ActorType.dispatchofpreload, context.actorOf(DispatchOfPreload.props("dispatchofpreload"), "dispatchofpreload"))

  }

  private def loadClusterModule = {
    context.actorOf(MemberListener.props("memberlistener"), "memberlistener")
  }

  private def loadGensisModule:Any={
    pe.register(CFRDActorType.ActorType.gensisblock,context.actorOf(GenesisBlocker.props("gensisblock"), "gensisblock"))

  }

  //启动共识模块，不同的共识方式启动的actor也不相同，继承模块需要重载此方法
  override def startupConsensus: Unit = ???

  //装载共识模块，继承模块需要重载此方法
  override def loadConsensusModule: Unit = ???


  override def receive: Receive = {
    case IModuleManager.startup_Consensus =>
      RepLogger.trace(RepLogger.System_Logger, this.getLogMsgPrefix(s"recv startup command,systemname=${this.sysTag}"))
      loadAutoTestStub
      startupConsensus
    case _ => //ignore
      RepLogger.trace(RepLogger.System_Logger, this.getLogMsgPrefix(s"recv unknow command,it is not startup command,systemname=${this.sysTag}"))
  }

}


class ConfigerHelper(conf: Config, tag: String, dbTag: String) {

  def init(): Unit = {
    authInitByCfg(tag)
    dbInit(dbTag)
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
    /*val mykeyPath = conf.getString("akka.remote.netty.ssl.security.base-path") + sysTag + ".jks"
    val psw = conf.getString("akka.remote.netty.ssl.security.key-store-password")
    val trustPath = conf.getString("akka.remote.netty.ssl.security.trust-store-mm")
    val trustPwd = conf.getString("akka.remote.netty.ssl.security.trust-store-password-mm")*/

    val mykeyPath = conf.getString("akka.remote.artery.ssl.config-ssl-engine.base-path") + sysTag + ".jks"
    val psw = conf.getString("akka.remote.artery.ssl.config-ssl-engine.key-store-password")
    val trustPath = conf.getString("akka.remote.artery.ssl.config-ssl-engine.trust-store-mm")
    val trustPwd = conf.getString("akka.remote.artery.ssl.config-ssl-engine.trust-store-password-mm")
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
