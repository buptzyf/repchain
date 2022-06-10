package rep.app.system

import java.util.concurrent.ConcurrentHashMap

import javax.net.ssl.SSLContext
import rep.app.conf.{RepChainConfig, SystemCertList, TimePolicy}
import rep.authority.cache.PermissionCacheManager
import rep.authority.check.PermissionVerify
import rep.crypto.Sha256
import rep.crypto.cert.{CryptoMgr, ISigner, ImpECDSASigner, SignTool}
import rep.log.RepLogger
import rep.log.httplog.HttpLogger
import rep.network.autotransaction.TransactionBuilder
import rep.network.tools.transpool.PoolOfTransaction
import rep.storage.chain.block.{BlockSearcher, BlockStorager}
import rep.storage.chain.preload.BlockPreload

class RepChainSystemContext(systemName:String) {
  private val config : RepChainConfig = new RepChainConfig(systemName)
  private val timePolicy : TimePolicy = new TimePolicy(config.getSystemConf)
  private val poolOfTransaction : PoolOfTransaction =  new PoolOfTransaction(this)
  private val cryptoManager : CryptoMgr = new  CryptoMgr(this)
  private val signer : ISigner = new ImpECDSASigner(this)
  private val signTool:SignTool = new SignTool(this)
  private val blockStorager : BlockStorager = new BlockStorager(this)
  private val blockPreloads : ConcurrentHashMap[String,BlockPreload] = new ConcurrentHashMap[String,BlockPreload]()
  private val systemCertList : SystemCertList = new SystemCertList(this)
  private val httpLogger = new HttpLogger(config.getChainNetworkId, config.getOuputAlertThreads,config.getOutputMaxThreads,config.getOutputAlertAliveTime,
                                          config.isOutputAlert,config.getOutputAlertPrismaUrl)
  private val transactionBuilder:TransactionBuilder = new TransactionBuilder(this.signTool)
  private val permissionCacheManager:PermissionCacheManager = PermissionCacheManager.getCacheInstance(this)
  private val permissionVerify : PermissionVerify =  new PermissionVerify(this)
  private val hashTool : Sha256 = new Sha256(this.cryptoManager.getInstance)

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
