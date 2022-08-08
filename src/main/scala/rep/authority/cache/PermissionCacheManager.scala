package rep.authority.cache

import java.util.concurrent.ConcurrentHashMap

import rep.app.system.RepChainSystemContext
import rep.authority.cache.PermissionCacheManager.CommonDataOfCache
import rep.log.RepLogger
import rep.proto.rc2.ChaincodeId
import rep.sc.tpl.did.DidTplPrefix
import rep.storage.db.common.IDBAccess
import rep.storage.filesystem.FileOperate


/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-19
 * @category	根据系统名称获取账户权限相关的缓存实例
 */
class PermissionCacheManager private(systemKey:String,cd:CommonDataOfCache) {
  private val signerCache = new SignerCache(cd,this)
  private val authenticateBindToCertCache = new AuthenticateBindToCertCache(cd,this)
  private val authenticateCache = new AuthenticateCache(cd,this)
  private val certificateCache = new CertificateCache(cd,this)
  private val certificateHashCache = new CertificateHashCache(cd,this)
  private val operateCache = new OperateCache(cd,this)
  private val authIdxCache = new AuthenticateIndexCache(cd,this)
  private val operIdxCache = new OperateIndexCache(cd,this)

  def registerBusinessNet(networkName:String):Unit={
    signerCache.registerBusinessNet(networkName)
    authenticateBindToCertCache.registerBusinessNet(networkName)
    authenticateCache.registerBusinessNet(networkName)
    certificateCache.registerBusinessNet(networkName)
    certificateHashCache.registerBusinessNet(networkName)
    operateCache.registerBusinessNet(networkName)
    authIdxCache.registerBusinessNet(networkName)
    operIdxCache.registerBusinessNet(networkName)
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-19
   * @category	获取账户权限相关的缓存实例
   * @param	prefix String 将要建立的缓存对象的类型
   * @return	如果成功返回ICache实例，否则为null
   */
  def getCache(prefix:String): ICache = {
    var obj: ICache = null
    prefix match {
        case DidTplPrefix.signerPrefix=> obj = this.signerCache
        case DidTplPrefix.bindPrefix=> obj = this.authenticateBindToCertCache
        case DidTplPrefix.authPrefix=> obj = this.authenticateCache
        case DidTplPrefix.certPrefix=> obj = this.certificateCache
        case DidTplPrefix.hashPrefix=> obj = this.certificateHashCache
        case DidTplPrefix.operPrefix=> obj = this.operateCache
        case DidTplPrefix.authIdxPrefix=> obj = this.authIdxCache
        case DidTplPrefix.operIdxPrefix=> obj = this.operIdxCache
      }
    obj
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-19
   * @category 出块之后更新账户权限相关缓存
   * @param key:String 状态key
   * @return
   * */
  def updateCache(key:String):Unit={
    if(key.indexOf("_"+DidTplPrefix.operPrefix)>0){
        this.operateCache.updateCache(key)
    }else if(key.indexOf("_"+DidTplPrefix.authPrefix)>0){
        this.authenticateCache.updateCache(key)
    }else if(key.indexOf("_"+DidTplPrefix.signerPrefix)>0){
        this.signerCache.updateCache(key)
    }else if(key.indexOf("_"+DidTplPrefix.certPrefix)>0) {
        this.certificateCache.updateCache(key)
    }else if(key.indexOf("_"+DidTplPrefix.bindPrefix)>0) {
        this.authenticateBindToCertCache.updateCache(key)
    }else if(key.indexOf("_"+DidTplPrefix.hashPrefix)>0) {
        this.certificateHashCache.updateCache(key)
    }else if(key.indexOf("_"+DidTplPrefix.authIdxPrefix)>0) {
      this.authIdxCache.updateCache(key)
    }else if(key.indexOf("_"+DidTplPrefix.operIdxPrefix)>0) {
      this.operIdxCache.updateCache(key)
    }
  }
}

object PermissionCacheManager {
  case class CommonDataOfCache(db:IDBAccess,baseNetName:String,cid:ChaincodeId,cacheSize:Int)
  private val cacheInstances = new ConcurrentHashMap[String, PermissionCacheManager]()

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-05-18
   * @category	根据数据库路径缓存实例
   * @return	如果成功返回PermissionCacheManager实例，否则为null
   */
  def getCacheInstance(systemKey:String,cd:CommonDataOfCache): PermissionCacheManager = {
    var instance: PermissionCacheManager = null
    //val key = FileOperate.mergeFilePath(Array[String](ctx.getConfig.getStorageDBPath,ctx.getConfig.getStorageDBName))
    if (cacheInstances.containsKey(systemKey)) {
      RepLogger.trace(RepLogger.Storager_Logger,s"CacheInstance exist, key=${systemKey}")
      instance = cacheInstances.get(systemKey)
    } else {
      RepLogger.trace(RepLogger.Storager_Logger,s"CacheInstance not exist,create new Instance, key=${systemKey}")
      instance = new PermissionCacheManager(systemKey,cd)
      val old = cacheInstances.putIfAbsent(systemKey,instance)
      if(old != null){
        instance = old
      }
    }
    instance
  }
}
