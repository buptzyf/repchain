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

package rep.app.system

import java.io.File

import akka.actor.{ ActorRef, ActorSystem, Address, Props }
import akka.cluster.Cluster
import com.typesafe.config.{ Config, ConfigFactory }
import rep.app.conf.SystemConf
import rep.app.system.ClusterSystem.InitType
import rep.network.base.ModuleBase
import rep.network.module.ModuleManager
import rep.utils.GlobalUtils.ActorType
import rep.storage.cfg._
import java.io.File
import scala.collection.mutable
import rep.app.conf.SystemProfile
import com.typesafe.config.ConfigValueFactory
import java.util.List
import java.util.ArrayList
import org.slf4j.LoggerFactory
import rep.log.trace._
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
 * @param initType 初始化类型
 * @param sysStart 是否开启system（不开启仅用于初始化）
 */
class ClusterSystem(sysTag: String, initType: Int, sysStart: Boolean) {
  protected def log = LoggerFactory.getLogger(this.getClass)

  private val USER_CONFIG_PATH = "conf/system.conf"

  private val modulePrefix = "RepCluster"

  private val moduleName = modulePrefix + "_" + sysTag

  private var webSocket: ActorRef = null

  private var memberLis: ActorRef = null

  private var moduleManager: ActorRef = null

  private var statistics: ActorRef = null

  private var enableWebSocket = false

  private var enableStatistic = false

  private var sysConf: Config = initSystem(sysTag)

  private var sysActor: ActorSystem = null

  private var clusterAddr: Address = null

  /**
   * 是否开启Web Socket（API）
   */
  def enableWS() = enableWebSocket = true
  def disableWS() = enableWebSocket = false

  /**
   * 获取用户和系统的联合配置
   * @param userConfigFilePath
   * @return
   */
  def getUserCombinedConf(userConfigFilePath: String): Config = {
    val userConfFile = new File(userConfigFilePath)
    val innerConf = ConfigFactory.load()
    userConfFile.exists() match {
      case true =>
        val combined_conf = ConfigFactory.parseFile(userConfFile).withFallback(innerConf)
        val final_conf = ConfigFactory.load(combined_conf)
        final_conf
      case false =>
        RepLogger.logWarn(sysTag, ModuleType.clustersystem, moduleName + " ~ " + "Couldn't find the user config file" + " ~ ")
        innerConf
    }
  }

  /**
   * 获取完整配置信息
   * 用户系统初始化
   * @param sysName
   * @return
   */
  def getConfigBySys(sysName: String): Config = {
    val myConfig =
      ConfigFactory.parseString("akka.remote.netty.ssl.security.key-store = \"jks/" + sysName +
        ".jks\"")
    val regularConfig = getUserCombinedConf(USER_CONFIG_PATH)
    val combined =
      myConfig.withFallback(regularConfig)
    val complete =
      ConfigFactory.load(combined)
    complete
  }

  def getConf = sysConf

  def hasDiskSpace: Boolean = {
    var b = true
    val sc: StoreConfig = StoreConfig.getStoreConfig()
    val ds = sc.getFreeDiskSpace / (1000 * 1000)
    if (SystemProfile.getDiskSpaceAlarm >= ds) {
      b = false
    }
    b
  }

  /**
   * 初始化系统参数
   * @param sysName
   * @return
   */
  def initSystem(sysName: String): Config = {
    val conf = getConfigBySys(sysName)
    RepLogger.logInfo(sysTag, ModuleType.clustersystem, moduleName + " ~ " + "System configuration successfully" + " ~ ")
    enableWebSocket = conf.getInt("system.ws_enable") match {
      case 0 => false
      case 1 => true
    }
    enableStatistic = conf.getInt("system.statistic_enable") match {
      case 0 => false
      case 1 => true
    }
    conf
  }

  def getClusterAddr = clusterAddr

  /**
   * 组网
   * @param address
   * @return
   */
  def joinCluster(address: Address): Boolean = {
    initType match {
      case InitType.SINGLE_INIT =>
        Cluster(sysActor)
      case InitType.MULTI_INIT =>
        Cluster(sysActor).join(address)
    }
    true
  }

  /**
   * 初始化
   */
  def init = {
    initConsensusNodeOfConfig
    sysStart match {
      case true =>
        sysActor = ActorSystem(SystemConf.SYSTEM_NAME, sysConf)
        clusterAddr = Cluster(sysActor).selfAddress
      case false => //ignore
    }
    RepLogger.logInfo(sysTag, ModuleType.clustersystem, "System" + " ~ " + s"System(${sysTag}) init successfully" + " ~ ")
  }

  private def initConsensusNodeOfConfig = {
    val nodelist = sysConf.getStringList("system.vote.vote_node_list")
    if (nodelist.contains(this.sysTag)) {
      var roles: List[String] = new ArrayList[String]
      roles.add("CRFD-Node")
      sysConf = sysConf.withValue("akka.cluster.roles", ConfigValueFactory.fromAnyRef(roles))
    }
  }

  /**
   * 启动系统
   */
  def start = {
    SystemProfile.initConfigSystem(sysActor.settings.config)
    moduleManager = sysActor.actorOf(ModuleManager.props("modulemanager", sysTag, enableStatistic, enableWebSocket,true), "modulemanager")

    if (!hasDiskSpace) {
      Cluster(sysActor).down(clusterAddr)
      throw new Exception("not enough disk space")
    }

    try {
      if (!verify4Storage.verify(sysTag)) {
        throw new Exception("Storager Verify Error")
      }
    } catch {
      case e: Exception => {
        throw new Exception("Storager Verify Error,info:" + e.getMessage)
      }
    }

    RepLogger.logInfo(sysTag, ModuleType.clustersystem, "System" + " ~ " + s"ClusterSystem ${sysTag} start" + " ~ ")
  }

}
