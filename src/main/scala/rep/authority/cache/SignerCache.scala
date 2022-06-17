package rep.authority.cache

import java.util.concurrent.ConcurrentHashMap

import rep.app.system.RepChainSystemContext
import rep.log.RepLogger
import rep.proto.rc2.Signer
import rep.sc.tpl.did.DidTplPrefix
import rep.storage.chain.preload.BlockPreload
import rep.utils.IdTool

import scala.collection.mutable.ArrayBuffer

object SignerCache{
  case class signerData(did:String,signer_valid:Boolean,opIds:ConcurrentHashMap[String,ArrayBuffer[String]],
                        certNames:Seq[String],createTime:_root_.scala.Option[com.google.protobuf.timestamp.Timestamp])
}

class SignerCache(ctx : RepChainSystemContext) extends ICache(ctx){
  import SignerCache._

  protected def getOpIdInAuthid(authid:String,blockPreload: BlockPreload):List[String]={
    RepLogger.Permission_Logger.trace(s"SignerCache.getOpidInAuthid ,key=${authid}")
    val auth = ctx.getPermissionCacheManager.getCache(DidTplPrefix.authPrefix)
    if(auth != null){
      val auth_cache = auth.asInstanceOf[AuthenticateCache]
      val data = auth_cache.get(authid,blockPreload)
      if(data == None) List[String]() else data.get.ops
    }else{
      List[String]()
    }
  }

  protected def getAuthenticates(creditCode:String,blockPreload: BlockPreload):Array[String]={
    val authIdx = ctx.getPermissionCacheManager.getCache(DidTplPrefix.authIdxPrefix)
    if(authIdx != null){
      val authIdx_cache = authIdx.asInstanceOf[AuthenticateIndexCache]
      val data = authIdx_cache.get(creditCode+DidTplPrefix.authIdxSuffix,blockPreload)
      if(data == None) Array[String]() else data.get
    }else{
      Array[String]()
    }
  }

  override protected def dataTypeConvert(any: Option[Any],blockPreload: BlockPreload): Option[Any] = {
    if(any == None){
      None
    }else{
      var sd : Option[signerData] = None
      val signer = any.get.asInstanceOf[Signer]
      val opIds:ConcurrentHashMap[String,ArrayBuffer[String]] = new ConcurrentHashMap[String,ArrayBuffer[String]]()
      RepLogger.Permission_Logger.trace(s"ISignerCache.signerToSignerData ,key=${signer.creditCode}")
      if(signer != null) {
        var authList = this.getAuthenticates(DidTplPrefix.authIdxPrefix + signer.creditCode + DidTplPrefix.authIdxSuffix,blockPreload)
        //同时可以从账户名下获取授权信息
        /*if(!signer.authorizeIds.isEmpty){
          if(!authList.isEmpty){
            authList = Array.concat(authList,signer.authorizeIds.toArray)
          }else{
            authList = signer.authorizeIds.toArray
          }
        }*/
        if(!authList.isEmpty) {
          RepLogger.Permission_Logger.trace(s"ISignerCache.signerToSignerData find Signer`s auth ,key=${signer.creditCode}")
          authList.foreach(f=>{
            val opList = getOpIdInAuthid(f,blockPreload)
            if(!opList.isEmpty){
              opList.foreach(k=>{
                var ab : ArrayBuffer[String] = new ArrayBuffer[String]()
                if(opIds.contains(k)){
                  ab = opIds.get(k)
                  if(ab.indexOf(f) < 0){
                    ab += f
                    opIds.put(k,ab)
                  }
                }else{
                  ab += f
                  opIds.put(k,ab)
                }
              })
            }
          })
        }

        sd = Some(signerData(signer.creditCode,signer.signerValid,opIds,signer.certNames,signer.createTime))
      }
      sd
    }
  }

  override protected def getBaseNetworkPrefix: String = {
    if(IdTool.isDidContract(ctx.getConfig.getAccountContractName)){
      this.common_prefix + IdTool.WorldStateKeySeparator + DidTplPrefix.signerPrefix
    }else{
      this.common_prefix + IdTool.WorldStateKeySeparator
    }
  }

  override protected def getBusinessNetworkPrefix: String = {
    if(IdTool.isDidContract(ctx.getConfig.getAccountContractName)){
      this.business_prefix + IdTool.WorldStateKeySeparator + DidTplPrefix.signerPrefix
    }else{
      this.business_prefix + IdTool.WorldStateKeySeparator
    }
  }

  /*override protected def getPrefix: String = {
    if(IdTool.isDidContract(ctx.getConfig.getAccountContractName)){
      this.common_prefix + IdTool.WorldStateKeySeparator + DidTplPrefix.signerPrefix
    }else{
      this.common_prefix + IdTool.WorldStateKeySeparator
    }

  }*/

  def get(key:String,blockPreload: BlockPreload):Option[signerData]={
    val d = this.getData(key,blockPreload)
    if(d == None)
      None
    else
      Some(d.get.asInstanceOf[signerData])
  }

  override protected def getCacheType: String = {
    IdTool.WorldStateKeySeparator + DidTplPrefix.signerPrefix
  }
}
