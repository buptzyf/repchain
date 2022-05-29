package rep.network.module

import akka.actor.Props
import com.typesafe.config.Config
import rep.app.conf.{ TimePolicy}
import rep.log.httplog.AlertInfo
import rep.log.{RepLogger, RepTimeTracer}
import rep.network.autotransaction.PeerHelper
import rep.network.base.ModuleBase
import rep.network.cluster.MemberListener
import rep.network.module.cfrd.CFRDActorType
import rep.network.transaction.DispatchOfPreload
import rep.sc.TransactionDispatcher
import rep.storage.verify.verify4Storage
import rep.ui.web.EventServer
import rep.network.genesis.GenesisBlocker


/**
 * Created by jiangbuyun on 2020/03/15.
 * 模块管理的基础类，启动时加载默认的公共的actor，然后根据不同的共识协议，加载不同的共识actor
 * 不同的协议需要继承这个基础类
 */
object IModuleManager {
  def props(name: String, isStartup: Boolean): Props = Props(classOf[IModuleManager], name, isStartup: Boolean)

  //启动共识模块的消息，这个消息的发送前提是系统同步已经完成，消息的发送者为同步模块
  case object startup_Consensus
}

class IModuleManager(moduleName: String, isStartup: Boolean) extends ModuleBase(moduleName) with IModule{
  init

  if (!checkSystemStorage) {
    context.system.terminate()
  }


  loadCommonActor
  loadConsensusModule

  private def checkSystemStorage: Boolean = {
    var r = true
    try {
      if (!new verify4Storage(pe.getRepChainContext).verify(pe.getSysTag)) {
        RepLogger.sendAlertToDB(pe.getRepChainContext.getHttpLogger(),new AlertInfo("SYSTEM",1,s"Node Name=${pe.getSysTag},BlockChain file error."))
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

  def loadSecurityInfo(conf:Config):Unit={
    val cryptoMgr = pe.getRepChainContext.getCryptoMgr
    val mykeyPath = cryptoMgr.getKeyFileSuffix.substring(1)+ java.io.File.separatorChar + pe.getSysTag + cryptoMgr.getKeyFileSuffix
    val psw = conf.getString("akka.remote.artery.ssl.config-ssl-engine.key-store-password")
    //val trustPath = conf.getString("akka.remote.artery.ssl.config-ssl-engine.trust-store-mm")
    val trustPath = cryptoMgr.getKeyFileSuffix.substring(1)+ java.io.File.separatorChar+pe.getRepChainContext.getConfig.getGMTrustStoreName + cryptoMgr.getKeyFileSuffix
    val trustPwd = conf.getString("akka.remote.artery.ssl.config-ssl-engine.trust-store-password-mm")
    pe.getRepChainContext.getSignTool.loadPrivateKey(pe.getSysTag, psw, mykeyPath)
    pe.getRepChainContext.getSignTool.loadNodeCertList(trustPwd, trustPath)
    RepLogger.info(RepLogger.System_Logger,  "密钥初始化装载完成...")
  }

  //初始化系统actor，完成公共actor的装载，包括证书、配置信息的装载，也包括存储的检查
  private def init: Unit = {
    val conf = this.pe.getRepChainContext.getConfig.getSystemConf//context.system.settings.config
    pe.register(ModuleActorType.ActorType.modulemanager,self)
    loadSecurityInfo(conf)
    TimePolicy.initTimePolicy(conf)
    pe.getRepChainContext.getTransactionPool.restoreCachePoolFromDB
    RepLogger.info(RepLogger.System_Logger,  "模块管理者初始化完成...")
  }

  private def loadCommonActor:Unit = {
    loadApiModule
    loadTransModule
    loadGensisModule
    loadClusterModule
    RepLogger.info(RepLogger.System_Logger,  "公共模块装载完成...")
  }

  private def loadApiModule = {
    if (pe.getRepChainContext.getConfig.getEnableStatistic) RepTimeTracer.openTimeTrace else RepTimeTracer.closeTimeTrace
    if (pe.getRepChainContext.getConfig.getEnableWebSocket) {
      pe.register(ModuleActorType.ActorType.webapi,context.system.actorOf(Props[EventServer], "webapi"))
    }
  }

  private def loadAutoTestStub:Any = {
    if (pe.getRepChainContext.getConfig.IsAutoCreateTransaction ) {
      pe.register(ModuleActorType.ActorType.peerhelper,context.actorOf(PeerHelper.props("peerhelper"), "peerhelper"))
    }
  }

  private def loadTransModule:Any={
    //if (this.isStartup) {
      pe.register(ModuleActorType.ActorType.transactiondispatcher, context.actorOf(TransactionDispatcher.props("transactiondispatcher").withDispatcher("contract-dispatcher"), "transactiondispatcher"))
    //}
    pe.register(ModuleActorType.ActorType.dispatchofpreload, context.actorOf(DispatchOfPreload.props("dispatchofpreload").withDispatcher("contract-dispatcher"), "dispatchofpreload"))
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
      RepLogger.trace(RepLogger.System_Logger, this.getLogMsgPrefix(s"recv startup command,systemname=${pe.getSysTag}"))
      loadAutoTestStub
      startupConsensus
    case _ => //ignore
      RepLogger.trace(RepLogger.System_Logger, this.getLogMsgPrefix(s"recv unknow command,it is not startup command,systemname=${pe.getSysTag}"))
  }

}
