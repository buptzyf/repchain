package rep.authority.cache

import rep.app.system.RepChainSystemContext
import rep.sc.tpl.did.DidTplPrefix

/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-19
 * @category	根据系统名称获取账户权限相关的缓存实例
 */
class PermissionCacheManager(ctx : RepChainSystemContext) {
  private val signerCache = new SignerCache(ctx)
  private val authenticateBindToCertCache = new AuthenticateBindToCertCache(ctx)
  private val authenticateCache = new AuthenticateCache(ctx)
  private val certificateCache = new CertificateCache(ctx)
  private val certificateHashCache = new CertificateHashCache(ctx)
  private val operateCache = new OperateCache(ctx)

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
      }
    obj
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-19
   * @category 出块之后更新证书缓存,账户合约部署非DID合约
   * @param key:String 状态key
   * @return
   * */
  def updateCertCache(key:String):Unit={
    System.err.println(s"t=${System.currentTimeMillis()},entry PermissionCacheManager#######updateCertCache,key=${key}," +
      s"node=${this.ctx.getSystemName}")
    this.certificateCache.updateCache(key)
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
    System.err.println(s"t=${System.currentTimeMillis()},entry PermissionCacheManager#######updateCache,key=${key}," +
      s"node=${this.ctx.getSystemName}")
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
    }
  }
}
