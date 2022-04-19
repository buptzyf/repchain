package rep.authority.cache

import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._
import rep.sc.tpl.did.DidTplPrefix


/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-19
 * @category	根据系统名称获取账户权限相关的缓存实例
 */
object PermissionCacheManager {
  private implicit val instance = new ConcurrentHashMap[String, ICache]() asScala
  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-19
   * @category	根据系统名称获取账户权限相关的缓存实例
   * @param	systemName String 系统名称
   * @param	prefix String 将要建立的缓存对象的类型
   * @return	如果成功返回ICache实例，否则为null
   */
  def getCache(systemName: String,prefix:String): ICache = {
    var obj: ICache = null
    val key = systemName+prefix
    synchronized {
      if (instance.contains(key)) {
        obj = instance.get(key).getOrElse(null)
      } else {
        prefix matches {
          case DidTplPrefix.signerPrefix=> obj = new SignerCache(systemName)
          case DidTplPrefix.bindPrefix=> obj = new AuthenticateBindToCertCache(systemName)
          case DidTplPrefix.authPrefix=> obj = new AuthenticateCache(systemName)
          case DidTplPrefix.certPrefix=> obj = new CertificateCache(systemName)
          case DidTplPrefix.hashPrefix=> obj = new CertificateHashCache(systemName)
          case DidTplPrefix.operPrefix=> obj = new OperateCache(systemName)
        }
        if(obj != null)
          instance.put(key, obj)
      }
      obj
    }
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-19
   * @category 出块之后更新证书缓存,账户合约部署非DID合约
   * @param systemName:String 系统名
   * @param key:String 状态key
   * @return
   * */
  def updateCertCache(systemName: String,key:String):Unit={
    val certCache = PermissionCacheManager.getCache(systemName,DidTplPrefix.certPrefix).asInstanceOf[CertificateCache]
    if(certCache != null)
      certCache.updateCache(key)
  }
  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-19
   * @category 出块之后更新账户权限相关缓存
   * @param systemName:String 系统名
   * @param key:String 状态key
   * @return
   * */
  def updateCache(systemName: String,key:String):Unit={
    if(key.indexOf("_"+DidTplPrefix.operPrefix)>0){
      val opCache = PermissionCacheManager.getCache(systemName,DidTplPrefix.operPrefix).asInstanceOf[OperateCache]
      if(opCache != null)
        opCache.updateCache(key)
    }else if(key.indexOf("_"+DidTplPrefix.authPrefix)>0){
      val authCache = PermissionCacheManager.getCache(systemName,DidTplPrefix.authPrefix).asInstanceOf[AuthenticateCache]
      if(authCache != null)
        authCache.updateCache(key)
    }else if(key.indexOf("_"+DidTplPrefix.signerPrefix)>0){
      val signCache = PermissionCacheManager.getCache(systemName,DidTplPrefix.signerPrefix).asInstanceOf[SignerCache]
      if(signCache != null)
        signCache.updateCache(key)
    }else if(key.indexOf("_"+DidTplPrefix.certPrefix)>0) {
      val certCache = PermissionCacheManager.getCache(systemName,DidTplPrefix.certPrefix).asInstanceOf[CertificateCache]
      if(certCache != null)
        certCache.updateCache(key)
    }else if(key.indexOf("_"+DidTplPrefix.bindPrefix)>0) {
      val bind = PermissionCacheManager.getCache(systemName,DidTplPrefix.bindPrefix).asInstanceOf[AuthenticateBindToCertCache]
      if(bind != null)
        bind.updateCache(key)
    }else if(key.indexOf("_"+DidTplPrefix.hashPrefix)>0) {
      val certHashCache = PermissionCacheManager.getCache(systemName,DidTplPrefix.hashPrefix).asInstanceOf[CertificateHashCache]
      if(certHashCache != null)
        certHashCache.updateCache(key)
    }
  }
}
