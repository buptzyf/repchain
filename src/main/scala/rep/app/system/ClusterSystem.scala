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

package rep.app.system

import akka.actor.{ActorRef, ActorSystem, Address, ExtensionIdProvider}
import akka.cluster.Cluster
import com.typesafe.config.Config
import rep.network.module.cfrd.ModuleManagerOfCFRD
import com.typesafe.config.ConfigValueFactory
import java.util.List
import java.util.ArrayList

import akka.util.Timeout
import org.slf4j.LoggerFactory
import rep.app.conf.RepChainConfig
import rep.crypto.nodedynamicmanagement.ReloadableTrustManagerTest4Inner
import rep.log.RepLogger

import scala.concurrent.Await
import scala.concurrent.duration._
import rep.log.httplog.AlertInfo
import rep.network.module.pbft.ModuleManagerOfPBFT
import rep.network.module.raft.ModuleManagerOfRAFT
import rep.network.tools.PeerExtension
import rep.storage.filesystem.factory.FileFactory
import rep.storage.verify.verify4Storage

/**
 * System创建伴生对象
 * @author shidianyue
 * @version	0.7
 * @update 2018-05 jiangbuyun
 */
object ClusterSystem {

  /**
   * 初始化类型
   */
  object InitType {
    val SINGLE_INIT = 1 //单机单节点
    val MULTI_INIT = 2 //单机多节点
  }
}
/**
 * System创建类
 * @author shidianyue
 * @version	0.7
 * @since	1.0
 * @param sysTag 系统system命名
 * @param isStartupClusterSystem 是否启动集群系统（不启动：仅用于集群系统建立不运行；启动：集群系统建立并且运行）
 */
class ClusterSystem(sysTag: String, isStartupClusterSystem: Boolean) {
  protected def log = LoggerFactory.getLogger(this.getClass)

  val SYSTEM_NAME = "Repchain"
  private var ctx : RepChainSystemContext = new RepChainSystemContext(sysTag)
  RepChainSystemContext.setCtx(sysTag,ctx)
  private var moduleManager: ActorRef = null
  private var sysConf: Config = ctx.getConfig.getSystemConf
  private var sysActor: ActorSystem = null
  private var clusterAddress: Address = null
  private var clusterOfInner : Cluster = null


  def getRepChainContext:RepChainSystemContext={
    this.ctx
  }

  def hasDiskSpace: Boolean = {
    FileFactory.checkFreeDiskSpace(ctx.getConfig)
  }

  def getClusterAddr = clusterAddress

  def getClusterInstance : Cluster = clusterOfInner

  /**
   * 初始化
   */
  def createClusterSystem = {
    initConsensusNodeOfConfig
    //建立ActorSystem
    RepLogger.info(RepLogger.System_Logger,  "建立ActorSystem...")
    sysActor = ActorSystem(SYSTEM_NAME, sysConf)
    val pe = PeerExtension(sysActor)
    pe.setRepChainContext(ctx)
    //注册系统在停止之前要做的处理
    sysActor.registerOnTermination(this.handlerOfBeforeTerminate)
    if (isStartupClusterSystem) {
      //启动集群
      clusterOfInner = Cluster(sysActor)
      clusterAddress = clusterOfInner.selfAddress
      RepLogger.info(RepLogger.System_Logger,  "集群已经启动...")
    }
    //在测试信任证书动态改变测试与跟踪时启用代码
    //val testTrustCertificate = new ReloadableTrustManagerTest4Inner(ctx)
    //testTrustCertificate.StartClusterStub
    RepLogger.trace(RepLogger.System_Logger, sysTag + "~" + "System" + " ~ " + s"System(${sysTag}) init successfully" + " ~ ")
  }

  def shutdown = {
    this.clusterOfInner.down(clusterAddress)
    System.err.println(s"shutdown ~~ address=${clusterAddress.toString},systemname=${this.sysTag}")
  }

  def terminateOfSystem={
    var r = true
    implicit val timeout = Timeout(120.seconds)
    try{
      val result = sysActor.terminate
      val result1 = Await.result(result, timeout.duration)//.asInstanceOf[Terminated]
      r = result1.getAddressTerminated
      this.ctx = null
    }catch{
      case e:Exception =>
        r = false
    }
    r
  }

  //停止之前处理
  private def handlerOfBeforeTerminate:Unit={
    //val txPool = ctx.getTransactionPool
    //txPool.saveCachePoolToDB
  }

  private def initConsensusNodeOfConfig = {
    val nodeList =  ctx.getConsensusNodeConfig.getVoteListOfConfig //sysConf.getStringList("system.vote.vote_node_list")
    if (nodeList.contains(this.sysTag)) {
      val roles: List[String] = new ArrayList[String]
      roles.add("CRFD-Node:" + this.sysTag)
      sysConf = sysConf.withValue("akka.cluster.roles", ConfigValueFactory.fromAnyRef(roles))
    }else{
      val roles: List[String] = new ArrayList[String]
      roles.add("CRFD-Backup-Node:" + this.sysTag)
      sysConf = sysConf.withValue("akka.cluster.roles", ConfigValueFactory.fromAnyRef(roles))
    }
    RepLogger.info(RepLogger.System_Logger,  "检查系统组网角色...")
  }

  /**
   * 启动系统
   */
  def startupRepChain = {
    //检查磁盘空间
    RepLogger.info(RepLogger.System_Logger,  "开始检查磁盘剩余空间...")
    if (!hasDiskSpace) {
      RepLogger.sendAlertToDB(ctx.getHttpLogger(),new AlertInfo("STORAGE",1,s"Node Name=${this.sysTag},Insufficient disk space."))
      //this.clusterOfInner.down(clusterAddress)
      RepLogger.info(RepLogger.System_Logger,  "系统剩余磁盘空间不够，系统自动终止...")
      terminateOfSystem
      throw new Exception("没有足够的磁盘空间")
    }

    loadSecurityInfo

    if (!checkSystemStorage) {
      terminateOfSystem
      throw new Exception("系统自检失败")
    }

    val typeConsensus = ctx.getConfig.getConsensustype
    if (typeConsensus == "CFRD") {
      moduleManager = sysActor.actorOf(ModuleManagerOfCFRD.props("modulemanager", true), "modulemanager")
    }else if(typeConsensus == "RAFT"){
      moduleManager = sysActor.actorOf(ModuleManagerOfRAFT.props("modulemanager", true), "modulemanager")
    }else if(typeConsensus == "PBFT"){
      moduleManager = sysActor.actorOf(ModuleManagerOfPBFT.props("modulemanager", true), "modulemanager")
    } else{
      RepLogger.error(RepLogger.System_Logger, sysTag + "~" + "System" + " ~ " + s"ClusterSystem ${sysTag} not startup,unknow consensus" + " ~ ")
    }

    RepLogger.trace(RepLogger.System_Logger, sysTag + "~" + "System" + " ~ " + s"ClusterSystem ${sysTag} start" + " ~ ")
  }

  private def checkSystemStorage: Boolean = {
    var r = true
    try {
      if (!new verify4Storage(this.ctx).verify(this.ctx.getSystemName)) {
        RepLogger.sendAlertToDB(ctx.getHttpLogger(),new AlertInfo("SYSTEM",1,s"Node Name=${ctx.getSystemName},BlockChain file error."))
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

  def loadSecurityInfo:Unit={
    val conf = ctx.getConfig
    val mykeyPath = conf.getKeyStore
    val psw = conf.getKeyPassword
    val trustPath = conf.getTrustStore
    val trustPwd = conf.getTrustPassword
    ctx.getSignTool.loadPrivateKey(ctx.getSystemName, psw, mykeyPath)
    //ctx.getSignTool.loadNodeCertList(trustPwd, trustPath)
    RepLogger.info(RepLogger.System_Logger,  "密钥初始化装载完成...")
  }

}
