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

import akka.actor.{ActorRef, ActorSystem, Address, Props}
import akka.cluster.Cluster
import com.typesafe.config.{Config, ConfigFactory}
import rep.app.conf.SystemConf
import rep.app.system.ClusterSystem.InitType
import rep.network.base.ModuleBase
import rep.network.cluster.MemberListener
import rep.network.module.ModuleManager
import rep.network.tools.Statistic.StatisticCollection
import rep.network.tools.register.ActorRegister
import rep.ui.web.EventServer
import rep.utils.GlobalUtils.ActorType
import rep.storage.cfg._ 
import java.io.File
import java.net.InetAddress

import scala.collection.mutable
import rep.app.conf.SystemProfile
import com.typesafe.config.ConfigValueFactory
import java.util.List
import java.util.ArrayList

import org.apache.commons.net.ntp.NTPUDPClient
import rep.log.trace.RepLogHelp
import rep.log.trace.LogType
import org.slf4j.LoggerFactory

import util.control.Breaks._

/**
  * System创建伴生对象
  * @author shidianyue
  * @version	0.7
  * @update 2018-05 jiangbuyun
  * */
object ClusterSystem {

  /**
    * 初始化类型
    */
  object InitType {
    val SINGLE_INIT = 1//单机单节点
    val MULTI_INIT = 2//单机多节点
  }

  private val actorRegisterList = mutable.HashMap[ String, ActorRegister ]()

  def register(systemName: String, actorRegister: ActorRegister) = actorRegisterList.put(systemName, actorRegister)

  def getActorRegister(sysName: String) = actorRegisterList.get(sysName)

  def unregister(systemName: String) = actorRegisterList.remove(systemName)

}
/**
  * System创建类
  * @author shidianyue
  * @version	0.7
  * @since	1.0
  * @param sysTag 系统system命名
  * @param initType 初始化类型
  * @param sysStart 是否开启system（不开启仅用于初始化）
  * */
class ClusterSystem(sysTag: String, initType: Int, sysStart:Boolean) {
  protected def log = LoggerFactory.getLogger(this.getClass)
  
  private val USER_CONFIG_PATH = "conf/system.conf"

  private val modulePrefix = "RepCluster"

  private val moduleName = modulePrefix + "_" + sysTag

  private var webSocket: ActorRef = null

  private var memberLis: ActorRef = null

  private var moduleManager: ActorRef = null

  private var statistics:ActorRef = null

  private var enableWebSocket = false

  private var enableStatistic = false

  private var sysConf: Config = initSystem(sysTag)

  private var sysActor:ActorSystem = null

  private var clusterAddr:Address = null

  /**
    * 是否开启Web Socket（API）
    */
  def enableWS() = enableWebSocket = true

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
        RepLogHelp.logMsg(log,LogType.WARN, moduleName +  " ~ " + "Couldn't find the user config file" + " ~ " )
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
    //TODO 将来找个路径也是可配置的
    val myConfig =
    ConfigFactory.parseString("akka.remote.netty.ssl.security.key-store = \"jks/mykeystore_" + sysName +
      ".jks\"")
    val regularConfig = getUserCombinedConf(USER_CONFIG_PATH)
    val combined =
      myConfig.withFallback(regularConfig)
    val complete =
      ConfigFactory.load(combined)
    complete
  }

  def getConf = sysConf

  def hasDiskSpace:Boolean={
    var b = true
    val sc : StoreConfig = StoreConfig.getStoreConfig()
    val ds = sc.getFreeDiskSpace/(1000*1000)
    if(SystemProfile.getDiskSpaceAlarm >= ds){
      b = false
    }
    b
  }

  /**
    * 各个时区一般只能算偏移量，不能确认对方是哪个时区，这一点是否需要考虑呢？
    * @return
    */
  def checkClockOffset: Boolean={
    val client = new NTPUDPClient()
    var offsetValue = 0L
    var offset = ""
    client.open()
    // 种子地址,seed-node要保证可达
    val seedsPathList = sysConf.getStringList("akka.cluster.seed-nodes")
    if (seedsPathList == null || seedsPathList.size() == 0) return true
    breakable {
      for (i <- 0 to seedsPathList.size - 1) {
        val seedPath = seedsPathList.get(i)
        val hostIpPort = seedPath.split("@")(1)
        val hostIp = hostIpPort.split(":")(0)
        val hostAddr = InetAddress.getByName(hostIp)
        val info = client.getTime(hostAddr)
        // compute offset
        info.computeDetails()
        offsetValue = info.getOffset()
        if (offsetValue != null) {
          offset = offsetValue.toString
          break()   // 直接break
        } else {
          offset = "N/A"
        }
      }
    }
    client.close()
    // TODO 日志以及误差控制写到配置文件中
    if (offset == "N/A") throw new Exception("没收到回应,请查看并确认NTP服务")
    if (math.abs(offsetValue) < 0.1*1000) true else false
  }
  
  /**
    * 初始化系统参数
    * @param sysName
    * @return
    */
  def initSystem(sysName: String): Config = {
    val conf = getConfigBySys(sysName)
    RepLogHelp.logMsg(log,LogType.INFO, moduleName +  " ~ " + "System configuration successfully" + " ~ " )
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
    ClusterSystem.register(sysTag, new ActorRegister)
    RepLogHelp.logMsg(log,LogType.INFO, "System" +  " ~ " + s"System(${sysTag}) init successfully" + " ~ " ,sysTag)
  }

  private def initConsensusNodeOfConfig={
    val nodelist = sysConf.getStringList("system.vote.vote_node_list")
    if(nodelist.contains(this.sysTag)){
      //val roles = Array("CRFD-Node")
      var roles :List[String] = new ArrayList[String]
      roles.add("CRFD-Node")
      sysConf = sysConf.withValue("akka.cluster.roles",  ConfigValueFactory.fromAnyRef(roles))
    }
  }
  
  /**
    * 启动系统
    */
  def start = {

//        if (!checkClockOffset){
//          Cluster(sysActor).down(clusterAddr)
//          throw new RuntimeException("与种子节点的时差超出设置值")
//        }

        if(enableStatistic) statistics = sysActor.actorOf(Props[StatisticCollection],"statistic")
        SystemProfile.initConfigSystem(sysActor.settings.config)
        moduleManager = sysActor.actorOf(ModuleManager.props("moduleManager", sysTag),"moduleManager")
        ModuleBase.registerActorRef(sysTag, ActorType.MODULE_MANAGER, moduleManager)
        
        if(!hasDiskSpace){
          Cluster(sysActor).down(clusterAddr)
          throw new Exception("not enough disk space")
        }
        if (enableWebSocket) webSocket = sysActor.actorOf(Props[ EventServer ], "ws")
        memberLis = sysActor.actorOf(Props[ MemberListener ], "memberListener")
        ModuleBase.registerActorRef(sysTag, ActorType.MEMBER_LISTENER, memberLis)
        if (enableWebSocket) ModuleBase.registerActorRef(sysTag, ActorType.API_MODULE, webSocket)
        if(enableStatistic) ModuleBase.registerActorRef(sysTag,ActorType.STATISTIC_COLLECTION, statistics)
    
        RepLogHelp.logMsg(log,LogType.INFO, "System" +  " ~ " + s"ClusterSystem ${sysTag} start" + " ~ " ,sysTag)
  }

  /**
    * 离网
    * @param clusterActor
    */
  def leaveCluster(clusterActor:ActorSystem): Unit ={
    Cluster(clusterActor).leave(getClusterAddr)
  }

  def getActorSys:ActorSystem = sysActor


}
