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

import akka.actor.{ActorRef, ActorSystem, Address}
import akka.cluster.Cluster
import com.typesafe.config.{Config}
import rep.network.module.cfrd.ModuleManagerOfCFRD
import com.typesafe.config.ConfigValueFactory
import java.util.List
import java.util.ArrayList
import akka.util.Timeout
import org.slf4j.LoggerFactory
import rep.log.RepLogger
import scala.concurrent.Await
import scala.concurrent.duration._
import rep.log.httplog.AlertInfo
import rep.network.module.pbft.ModuleManagerOfPBFT
import rep.network.module.raft.ModuleManagerOfRAFT
import rep.network.tools.PeerExtension
import rep.storage.filesystem.factory.FileFactory

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
  //private val modulePrefix = "RepCluster"
  //private val moduleName = modulePrefix + "_" + sysTag
  //private var webSocket: ActorRef = null
  //private var memberLis: ActorRef = null
  //private var statistics: ActorRef = null

  val SYSTEM_NAME = "Repchain"
  private val ctx : RepChainSystemContext = new RepChainSystemContext(sysTag)
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
    sysActor = ActorSystem(SYSTEM_NAME, sysConf)
    val pe = PeerExtension(sysActor)
    pe.setRepChainContext(ctx)
    //注册系统在停止之前要做的处理
    sysActor.registerOnTermination(this.handlerOfBeforeTerminate)
    if (isStartupClusterSystem) {
      //启动集群
      clusterOfInner = Cluster(sysActor)
      clusterAddress = clusterOfInner.selfAddress
    }

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
    }catch{
      case e:Exception =>
        r = false
    }
    r
  }

  //停止之前处理
  private def handlerOfBeforeTerminate:Unit={
    val txPool = ctx.getTransactionPool
    txPool.saveCachePoolToDB
  }

  private def initConsensusNodeOfConfig = {
    val nodeList = sysConf.getStringList("system.vote.vote_node_list")
    if (nodeList.contains(this.sysTag)) {
      val roles: List[String] = new ArrayList[String]
      roles.add("CRFD-Node:" + this.sysTag)
      sysConf = sysConf.withValue("akka.cluster.roles", ConfigValueFactory.fromAnyRef(roles))
    }
  }

  /**
   * 启动系统
   */
  def startupRepChain = {
    //检查磁盘空间
    if (!hasDiskSpace) {
      RepLogger.sendAlertToDB(ctx.getHttpLogger(),new AlertInfo("STORAGE",1,s"Node Name=${this.sysTag},Insufficient disk space."))
      //this.clusterOfInner.down(clusterAddress)
      terminateOfSystem
      throw new Exception("not enough disk space")
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

}
