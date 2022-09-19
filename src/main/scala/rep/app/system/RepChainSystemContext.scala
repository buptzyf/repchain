package rep.app.system


import java.util.concurrent.ConcurrentHashMap
import akka.actor.{ActorRef, Address}
import rep.accumulator.Accumulator.bitLength
import rep.accumulator.verkle.VerkleNodeBuffer
import rep.accumulator.{PrimeTool, Rsa2048}
import rep.app.conf.{RepChainConfig, SystemCertList, TimePolicy}
import rep.app.management.{ReasonOfStop, RepChainMgr}
import rep.authority.cache.PermissionCacheManager
import rep.authority.cache.PermissionCacheManager.CommonDataOfCache
import rep.authority.check.PermissionVerify
import rep.crypto.Sha256
import rep.crypto.cert.{CryptoMgr, ISigner, ImpECDSASigner, SignTool}
import rep.crypto.nodedynamicmanagement.ReloadableTrustManager
import rep.log.RepLogger
import rep.log.httplog.HttpLogger
import rep.network.autotransaction.TransactionBuilder
import rep.network.cluster.management.ConsensusNodeConfig
import rep.network.tools.NodeMgr
import rep.network.tools.transpool.PoolOfTransaction
import rep.proto.rc2.ChaincodeId
import rep.storage.chain.KeyPrefixManager
import rep.storage.chain.block.{BlockSearcher, BlockStorager}
import rep.storage.chain.preload.BlockPreload
import rep.storage.db.factory.DBFactory
import rep.storage.filesystem.FileOperate
import rep.utils.SerializeUtils

import java.math.BigInteger

class RepChainSystemContext (systemName:String){//},cs:ClusterSystem) {
  private val tx_acc_base_key = "tx_acc_base"
  private val state_acc_base_key = "state_acc_base"
  private val config : RepChainConfig = new RepChainConfig(systemName)
  private val timePolicy : TimePolicy = new TimePolicy(config.getSystemConf)
  private val cryptoManager : CryptoMgr = new  CryptoMgr(this)
  private val signer : ISigner = new ImpECDSASigner(this)
  private val signTool:SignTool = new SignTool(this)
  private val blockStorager : BlockStorager = new BlockStorager(this)
  private val blockPreloads : ConcurrentHashMap[String,BlockPreload] = new ConcurrentHashMap[String,BlockPreload]()
  private val poolOfTransaction : PoolOfTransaction =  new PoolOfTransaction(this)
  private val systemCertList : SystemCertList = new SystemCertList(this)
  private val httpLogger = new HttpLogger(config.getChainNetworkId, config.getOuputAlertThreads,config.getOutputMaxThreads,config.getOutputAlertAliveTime,
                                          config.isOutputAlert,config.getOutputAlertPrismaUrl)
  private val transactionBuilder:TransactionBuilder = new TransactionBuilder(this.signTool)

  private val permissionCacheManager:PermissionCacheManager = PermissionCacheManager.getCacheInstance(
    FileOperate.mergeFilePath(Array[String](config.getStorageDBPath,config.getStorageDBName)) + config.getIdentityNetName,
    CommonDataOfCache(DBFactory.getDBAccess(config),config.getIdentityNetName,ChaincodeId(config.getAccountContractName,
      config.getAccountContractVersion),config.getAccountCacheSize)
  )
  permissionCacheManager.registerBusinessNet(config.getChainNetworkId)
  private val permissionVerify : PermissionVerify =  new PermissionVerify(this)
  private val hashTool : Sha256 = new Sha256(this.cryptoManager.getInstance)
  private val reloadTrustStore : ReloadableTrustManager = ReloadableTrustManager.createReloadableTrustManager(this)
  private val consensusNodeConfig : ConsensusNodeConfig = new ConsensusNodeConfig(this)
  private val registerClusterNode:ConcurrentHashMap[String,Address] = new ConcurrentHashMap[String,Address]()
  private val nodemgr = new NodeMgr

  private val tx_acc_base : BigInteger = getAccBase(tx_acc_base_key)
  private val state_acc_base : BigInteger = getAccBase(state_acc_base_key)
  private val vb : VerkleNodeBuffer = new VerkleNodeBuffer(this)

  private var ml : ActorRef = null

  def registerMemberList(ml:ActorRef):Unit={
    this.ml = ml
  }

  def getMemberList: ActorRef = {
    this.ml
  }

  def getTxAccBase:BigInteger={
    this.tx_acc_base
  }

  def getStateAccBase:BigInteger={
    this.state_acc_base
  }

  def getVerkleNodeBuffer:VerkleNodeBuffer={
    this.vb
  }

  private def getAccBase(key:String):BigInteger={
    var r = PrimeTool.getPrimeOfRandom(bitLength, Rsa2048.getHalfModulus)
    val db = DBFactory.getDBAccess(this.getConfig)

    val v = db.getObject[String](KeyPrefixManager.getWorldStateKey(this.getConfig, key, "_"))
    if (v != None) {
      val vs = if(v.get.isInstanceOf[String]) v.get.asInstanceOf[String] else ""
      if(vs.equals("")){
        db.putBytes(KeyPrefixManager.getWorldStateKey(this.getConfig, key, "_"),SerializeUtils.serialise(r.toString()))
      }else{
        r = new BigInteger(vs)
      }
    }else{
      db.putBytes(KeyPrefixManager.getWorldStateKey(this.getConfig, key, "_"),SerializeUtils.serialise(r.toString()))
    }
    r
  }

  def getNodeMgr: NodeMgr = {
    this.nodemgr
  }

  def registerNode(name:String,address: Address):Unit={
    if(name != "")
      this.registerClusterNode.put(name,address)
  }

  /*def shutDownNode(del:Array[String]): Unit ={
    if(cs != null && cs.getClusterInstance != null){
      try{
        del.foreach(name=>{
          if(registerClusterNode.containsKey(name)){
            val address = this.registerClusterNode.getOrDefault(name,null)
            if(address != null){
              cs.getClusterInstance.leave(address)
              RepLogger.trace(RepLogger.System_Logger, s"RepChainSystemContext shutdown name=${name},address=${address}")
            }
          }
        })
      }catch {
        case ex:Exception=>
          RepLogger.info(RepLogger.System_Logger, "RepChainSystemContext shutdown="+del.mkString(",")+",error msg="+ex.getMessage)
      }
    }
  }*/

  def shutDownNode(del:Array[String]): Unit ={
    del.foreach(name=>{
      if(name.equalsIgnoreCase(this.systemName)){
        RepChainMgr.shutdown(systemName,ReasonOfStop.Manual)
        RepLogger.info(RepLogger.System_Logger, "RepChainSystemContext del trust store, shutdown="+name)
      }
    })
  }

  def getReloadTrustStore:ReloadableTrustManager={
    this.reloadTrustStore
  }

  def getConsensusNodeConfig:ConsensusNodeConfig={
    this.consensusNodeConfig
  }

  def getHashTool:Sha256={
    this.hashTool
  }

  def getPermissionVerify:PermissionVerify={
    this.permissionVerify
  }

  def getTransactionBuilder:TransactionBuilder={
    this.transactionBuilder
  }

  def getHttpLogger():HttpLogger={
    this.httpLogger
  }

  def getSystemCertList:SystemCertList={
    this.systemCertList
  }

  def getBlockSearch:BlockSearcher={
    new BlockSearcher(this)
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	根据实例id获取交易预执行实例
   * @param instanceId:String 实例id,systemName:String 系统名称
   * @return 返回BlockPreload预执行实例
   * */
  def getBlockPreload(instanceId:String):BlockPreload={
    var instance: BlockPreload = null
    synchronized {
      if (blockPreloads.containsKey(instanceId)) {
        instance = blockPreloads.get(instanceId)
      } else {
        instance = new BlockPreload(instanceId,this)
        val old = blockPreloads.putIfAbsent(instanceId,instance)
        if(old != null){
          instance = old
        }
      }
      instance
    }
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	根据实例id释放交易预执行实例
   * @param instanceId:String 实例id,systemName:String 系统名称
   * @return
   * */
  def freeBlockPreloadInstance(instanceId:String):Unit={
    try{
      val instance = this.blockPreloads.get(instanceId)
      if(instance != null){
        instance.free
      }
      this.blockPreloads.remove(instanceId)
    }catch {
      case e:Exception =>
        RepLogger.info(RepLogger.Storager_Logger,s"free preload instance failed,instanceId=${instanceId}," +
          s"systemName=${systemName},msg=${e.getCause}")
    }
  }

  def getConfig:RepChainConfig={
    this.config
  }

  def getTimePolicy:TimePolicy={
    this.timePolicy
  }

  def getCryptoMgr:CryptoMgr={
    this.cryptoManager
  }

  def getSigner : ISigner = {
    this.signer
  }

  def getSystemName:String={
    this.systemName
  }

  def getSignTool:SignTool={
    this.signTool
  }

  def getTransactionPool: PoolOfTransaction = {
    this.poolOfTransaction
  }

  def getPermissionCacheManager:PermissionCacheManager={
    this.permissionCacheManager
  }

  def getBlockStorager:BlockStorager={
    this.blockStorager
  }
}

object RepChainSystemContext{
  private val ctxs = new ConcurrentHashMap[String,RepChainSystemContext]()

  def setCtx(name:String,ctx:RepChainSystemContext):Unit={
    this.ctxs.put(name,ctx)
  }

  def getCtx(name:String):RepChainSystemContext={
    if(this.ctxs.containsKey(name)){
      this.ctxs.get(name)
    }else{
      null
    }
  }
}