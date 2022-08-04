package rep.app.conf

import java.io.File
import java.security.{Provider, Security}

import akka.japi.Util.immutableSeq
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import rep.log.RepLogger

import scala.collection.mutable
import scala.util.control.Breaks.{break, breakable}


/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-08
 * @category	RepChain节点启动装载节点配置文件，读取配置信息，所有配置项都从此类获取。
 * */
class RepChainConfig {
  val ConfigFileDir = "conf/"
  private var systemName:String = ""
  private var sysConf: Config = null

  def this(systemName:String){
    this()
    this.systemName = systemName
    this.loadConfig
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-08
   * @category	初始化装载配置内容
   */
  private def loadConfig:Unit={
    val userConfigFile = new File(ConfigFileDir+this.systemName+ File.separator+"system.conf")
    this.sysConf = ConfigFactory.load()
    if (userConfigFile.exists()) {
      var combined_conf = ConfigFactory.parseFile(userConfigFile).withFallback(this.sysConf)
      this.sysConf = ConfigFactory.load(combined_conf)
      setSSLConfig
      loadSecurityClass
    } else{
      RepLogger.trace(RepLogger.System_Logger, this.systemName + " ~ " + "ClusterSystem" + "~" + " custom configuration file not exist")
    }
    RepLogger.trace(RepLogger.System_Logger, this.systemName + " ~ " + "ClusterSystem" + "~" + "load System configuration successfully")
  }

  private def setSSLConfig:Unit={
    val networkId = this.getChainNetworkId
    val isUseGM = this.isUseGM
    val prefix = if(isUseGM) "pfx" else "jks"
    val base_path = prefix + "/" + networkId + "/"
    val key_store = base_path + this.systemName + "."+ prefix
    val trust_store = base_path + "mytruststore"+"." + prefix
    var myConfig =
      ConfigFactory.parseString("akka.remote.artery.ssl.config-ssl-engine.base-path = \"" + base_path + "\"")
    this.sysConf = myConfig.withFallback(this.sysConf)
    myConfig =
      ConfigFactory.parseString("akka.remote.artery.ssl.config-ssl-engine.key-store = \"" + key_store + "\"")
    this.sysConf = myConfig.withFallback(this.sysConf)
    myConfig =
      ConfigFactory.parseString("akka.remote.artery.ssl.config-ssl-engine.trust-store = \"" + trust_store + "\"")
    this.sysConf = myConfig.withFallback(this.sysConf)

    val protocol = if(isUseGM) "GMSSLv1.1" else "TLSv1.2"
    myConfig =
      ConfigFactory.parseString("akka.remote.artery.ssl.config-ssl-engine.protocol = \"" + protocol + "\"")
    this.sysConf = myConfig.withFallback(this.sysConf)

    myConfig =
      ConfigFactory.parseString("akka.remote.artery.ssl.config-ssl-engine.random-number-generator = \"" + "SecureRandom" + "\"")
    this.sysConf = myConfig.withFallback(this.sysConf)

    val algorithm = if(isUseGM) "[\"GMSSL_ECC_SM4_SM3\"]" else "[\"TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256\"]"
    myConfig =ConfigFactory.parseString("akka.remote.artery.ssl.config-ssl-engine.enabled-algorithms = " + algorithm + "")
    //val algorithm = if(isUseGM) Array("GMSSL_ECC_SM4_SM3") else Array("TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256")
    //val hm = new mutable.HashMap[String,Any]()
    //hm += "akka.remote.artery.ssl.config-ssl-engine.enabled-algorithms" -> algorithm
    //ConfigFactory.parseMap(hm)
    this.sysConf = myConfig.withFallback(this.sysConf)

    val p2pConfig = if(isUseGM) "rep.crypto.nodedynamicmanagement4gm.CustomGMSSLEngine" else "rep.crypto.nodedynamicmanagement.CustomSSLEngine"
    myConfig =
      ConfigFactory.parseString("akka.remote.artery.ssl.ssl-engine-provider = \"" + p2pConfig + "\"")
    this.sysConf = myConfig.withFallback(this.sysConf)

    myConfig =
      ConfigFactory.parseString("akka.remote.artery.ssl.config-ssl-engine.node-name = \"" + this.systemName + "\"")
    this.sysConf = myConfig.withFallback(this.sysConf)
  }

  private def loadSecurityClass:Unit={
    if(this.isUseGM){
      System.out.println("%%%%%%%%load BouncyCastleProvider and BouncyCastleJsseProvider ...%%%%%%%%")
      loadProvider(this.getGMProviderOfJCE,1)
      loadProvider(this.getGMJsseProvider,2)
      System.out.println("%%%%%%%%load BouncyCastleProvider and BouncyCastleJsseProvider finish%%%%%%%%")
    }
  }

  private def loadProvider(cname:String,serial:Int):Unit={
    try{
      val cls = this.getClass.getClassLoader.loadClass(cname)
      System.out.println(s"get getClassLoader = ${cls.getName}")
      val csts =  cls.getConstructors()
      System.out.println(s"get Constructors = ${csts.length}")
      if(csts.length > 0){
        breakable(
          csts.foreach(cst=>{
            if(cst.getParameterCount == 0){
              System.out.println(s"get Constructors0 = ${cst.getName},params=${cst.getParameterCount}")
              val p = cst.newInstance().asInstanceOf[Provider]
              System.out.println(s"get instance = ${p.getName}")
              Security.insertProviderAt(p,serial)
              //Security.addProvider(p)
              break
            }
          }))
      }
    }catch {
      case e:Exception => System.out.println(s"loadProvider error,msg=${e.getMessage}")
    }
  }

  def getBasePath:String={
    this.sysConf.getString("akka.remote.artery.ssl.config-ssl-engine.base-path")
  }

  def getKeyStore:String={
    this.sysConf.getString("akka.remote.artery.ssl.config-ssl-engine.key-store")
  }

  def getTrustStore:String={
    this.sysConf.getString("akka.remote.artery.ssl.config-ssl-engine.trust-store")
  }

  def getKeyPassword:String={
    this.sysConf.getString("akka.remote.artery.ssl.config-ssl-engine.key-password")
  }

  def getKeyStorePassword:String={
    this.sysConf.getString("akka.remote.artery.ssl.config-ssl-engine.key-store-password")
  }

  def getTrustPassword:String={
    this.sysConf.getString("akka.remote.artery.ssl.config-ssl-engine.trust-store-password")
  }

  def getProtocol:String={
    this.sysConf.getString("akka.remote.artery.ssl.config-ssl-engine.protocol")
  }

  def getAlgorithm:Set[String]={
    immutableSeq(this.sysConf.getStringList("akka.remote.artery.ssl.config-ssl-engine.enabled-algorithms")).toSet
  }

  def getSecureRandom:String={
    this.sysConf.getString("akka.remote.artery.ssl.config-ssl-engine.random-number-generator")
  }

  def getSystemName:String={
    this.systemName
  }

  def getSystemConf:Config={
    this.sysConf
  }

  def getMemberManagementContractName:String={
    this.sysConf.getString("system.member_management.contract_name")
  }

  def getMemberManagementContractMethod:String={
    this.sysConf.getString("system.member_management.contract_method")
  }

  def getMemberManagementContractVoteMethod:String={
    this.sysConf.getString("system.member_management.contract_vote_method")
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-08
   * @category	获取系统配置中的抽签节点（即共识节点）列表
   * @return	返回抽签节点列表List[String]，否则为null
   */
  def getVoteNodeList:List[String]={
    val temp = this.sysConf.getStringList("system.vote.vote_node_list")
      if(temp != null){
        val r = new Array[String](temp.size())
        temp.toArray(r)
        r.toList
      }else{
        null
      }
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-08
   * @category	设置当前节点的角色，当前节点是共识节点才会调用该设置函数
   * @param	roles List[String] 角色数组
   */
  def setSystemRole(roles:List[String]): Unit ={
    this.sysConf = this.sysConf.withValue("akka.cluster.roles", ConfigValueFactory.fromAnyRef(roles))
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-08
   * @category	是否开启websocket
   * @return	开启返回true，否则false
   */
  def getEnableWebSocket:Boolean={
    this.sysConf.getInt("system.api.ws_enable") match {
      case 0 => false
      case 1 => true
    }
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-08
   * @category	是否开启https
   * @return	开启返回true，否则false
   */
  def isUseHttps:Boolean={
    this.sysConf.getInt("system.api.http_mode") match {
      case 0 => false
      case 1 => true
    }
  }

  def isNeedClientAuth:Boolean={
    this.sysConf.getInt("system.api.is_need_client_auth") match {
      case 0 => false
      case 1 => true
    }
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-08
   * @category	是否开启出块相关的时间统计
   * @return	开启返回true，否则false
   */
  def getEnableStatistic:Boolean={
    this.sysConf.getInt("system.statistic_enable") match {
      case 0 => false
      case 1 => true
    }
  }

  def getLimitTransactionNumberOfBlock:Int={
    this.sysConf.getInt("system.block.trans_num_limit")
  }

  def getMinTransactionNumberOfBlock:Int={
    this.sysConf.getInt("system.block.trans_num_min")
  }

  def getBlockMaxLength:Int={
    this.sysConf.getInt("system.block.block_length")
  }

  def getMinVoteNumber:Int={
    this.sysConf.getInt("system.vote.vote_node_min")
  }

  def getAutoCreateTransactionInterval:Int={
    this.sysConf.getInt("system.transaction.tran_create_dur")
  }

  def getMaxCacheNumberOfTransaction:Int={
    this.sysConf.getInt("system.transaction.max_cache_num")
  }

  def IsAutoCreateTransaction:Boolean={
    this.sysConf.getInt("system.trans_create_type") match {
      case 0 => false
      case 1 => true
    }
  }

  def getMinDiskSpaceAlarm:Int={
    this.sysConf.getInt("system.disk_space_manager.disk_space_alarm")
  }

  def getHttpServicePort:Int={
    this.sysConf.getInt("system.api.http_service_port")
  }

  def getHttpServiceActorNumber:Int={
    this.sysConf.getInt("system.http_service_actor_number")
  }

  def isBroadcastTransaction:Boolean={
    this.sysConf.getInt("system.is_broadcast_transaction")match {
      case 0 => false
      case 1 => true
    }
  }

  def isCheckCertValidate:Boolean={
    this.sysConf.getInt("system.check_cert_validate") match {
      case 0 => false
      case 1 => true
    }
  }

  def getContractRunMode:Int={
    this.sysConf.getInt("system.contract_operation_mode")
  }

  def getAccountContractName:String={
    this.sysConf.getString("system.account.chain_code_name")
  }

  def getAccountContractVersion:Int={
    this.sysConf.getInt("system.account.chain_code_version")
  }

  def getAccountCacheSize:Int={
    this.sysConf.getInt("system.account.cache_size")
  }

  def getRetryTimeOfBlockFailed:Int={
    this.sysConf.getInt("system.block.retry_time")
  }

  def getGenesisNodeName:String={
    this.sysConf.getString("system.genesis_node_name")
  }

  def getChainCertName:String={
    this.sysConf.getString("system.chain_cert_name")
  }

  def getChainNetworkId:String={
    this.sysConf.getString("system.chain_network_id")
  }

  def getIdentityNetName:String={
    this.sysConf.getString("system.basic_chain_id")
  }

  def getTransactionNumberOfProcessor:Int={
    this.sysConf.getInt("system.number_of_transProcessor")
  }

  def hasPreloadOfApi:Boolean={
    this.sysConf.getBoolean("system.has_preload_trans_of_api")
  }

  def isVerifyOfEndorsement:Boolean={
    this.sysConf.getBoolean("system.is_verify_of_endorsement")
  }

  def getEndorsementNumberMode:Int={
    this.sysConf.getInt("system.number_of_endorsement")
  }

  def getBlockNumberOfRaft:Int={
    this.sysConf.getInt("system.consensus.block_number_of_raft")
  }

  def getConsensustype:String={
    this.sysConf.getString("system.consensus.type")
  }

  def isStreamBlock:Boolean={
    this.sysConf.getInt("system.consensus.is_stream") match {
      case 0 => false
      case 1 => true
    }
  }

  def isStartupRealtimeGraph:Boolean={
    this.sysConf.getInt("system.api.real_time_graph_enable") match {
      case 0 => false
      case 1 => true
    }
  }

  def getStorageDBPath:String={
    this.sysConf.getString("system.storage.db_path")
  }

  def getStorageDBName:String={
    var name = this.sysConf.getString("system.storage.db_name")
    if(name == null || name.equalsIgnoreCase("")){
      name = this.getSystemName
    }
    name
  }

  def getStorageDBType:String={
    this.sysConf.getString("system.storage.db_type")
  }

  def getStorageDBCacheSize:Int={
    this.sysConf.getInt("system.storage.db_cache_size")
  }

  def getStorageBlockFilePath:String={
    this.sysConf.getString("system.storage.block_file_path")
  }

  def getStorageBlockFileMaxLength:Int={
    this.sysConf.getInt("system.storage.file_max_length")
  }

  def getStorageBlockFileName:String={
    this.sysConf.getString("system.storage.block_file_name")
  }

  def getStorageBlockFileType:String={
    this.sysConf.getString("system.storage.block_file_type")
  }

  def isOutputAlert:Boolean={
    this.sysConf.getBoolean("system.output_alert.is_output_alert")
  }

  def getOuputAlertThreads:Int={
    this.sysConf.getInt("system.output_alert.core_threads")
  }

  def getOutputMaxThreads:Int={
    this.sysConf.getInt("system.output_alert.max_threads")
  }

  def getOutputAlertAliveTime:Int={
    this.sysConf.getInt("system.output_alert.alive_time")
  }

  def getOutputAlertPrismaUrl:String={
    this.sysConf.getString("system.output_alert.prisma_url")
  }

  def getConsensusSynchType:String={
    this.sysConf.getString("system.consensus.synch_type")
  }

  def getEndorsementResendTimes:Int={
    this.sysConf.getInt("system.time.timeout.endorse_resend_times")
  }

  def isPersistenceTransactionToDB:Boolean={
    this.sysConf.getInt("system.is_persistence_tx_to_db") match {
      case 0 => false
      case 1 => true
    }
  }

  def isUseGM:Boolean={
    this.sysConf.getBoolean("system.gm.is_use_gm")
  }

  def getGMProviderOfJCE:String={
    this.sysConf.getString("system.gm.gm_jce_provider")
  }

  def getGMProviderNameOfJCE:String={
    this.sysConf.getString("system.gm.gm_jce_provider_name")
  }

  /*def getGMTrustStoreName:String={
    this.sysConf.getString("system.gm.gm_trust_store_name")
  }*/

  def getGMJsseProviderName:String={
    this.sysConf.getString("system.gm.gm_jsse_provider_name")
  }

  def getGMJsseProvider:String={
    this.sysConf.getString("system.gm.gm_jsse_provider")
  }


  def getGMSignKeyName:String={
    this.sysConf.getString("system.gm.gm_pfx_sign_key_name")
  }
}

/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-08
 * @category	RepChainConfig实例通过此类获取。
 * */
/*
object RepChainConfig{
  val ConfigFileDir = "conf/"
  private val configInstances = new ConcurrentHashMap[String, RepChainConfig]()

  /*def registerConfig(systemName:String):Unit={
    synchronized {
      if (!configInstances.containsKey(systemName)) {
        configInstances.put(systemName, new RepChainConfig(systemName))
      }
    }
  }*/
  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-08
   * @category	根据系统名称获取系统都配置内容的实例
   * @param	systemName String 系统名称
   * @return	RepChainConfig，否则为null
   */
  def getSystemConfig(systemName: String): RepChainConfig = {
    var instance: RepChainConfig = null
    synchronized {
      if (configInstances.containsKey(systemName)) {
        instance = configInstances.get(systemName)
      } else {
        instance = new RepChainConfig(systemName)
        configInstances.put(systemName, instance)
      }
      instance
    }
  }
}*/