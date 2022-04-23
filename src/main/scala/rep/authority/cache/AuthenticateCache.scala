package rep.authority.cache

import rep.app.system.RepChainSystemContext
import rep.proto.rc2.Authorize
import rep.sc.tpl.did.DidTplPrefix
import rep.storage.chain.preload.BlockPreload

object AuthenticateCache{
  case class authData(authid:String,authorizeValid:Boolean,ops:List[String])
}

class AuthenticateCache(ctx : RepChainSystemContext) extends ICache(ctx) {
  import AuthenticateCache.authData

  override protected def dataTypeConvert(any: Option[Any],blockPreload: BlockPreload): Option[Any] = {
    if(any == None){
      None
    }else{
      var ad : Option[authData] = None
      val auth = any.get.asInstanceOf[Authorize]
      if(auth != null) {
        ad = Some(authData(auth.id,auth.authorizeValid,auth.opId.toList))
      }
      ad
    }
  }

  override protected def getPrefix: String = {
    this.common_prefix + this.splitSign + DidTplPrefix.authPrefix
  }

  def get(key:String,blockPreload: BlockPreload):Option[authData]={
    val d = this.getData(key,blockPreload)
    if(d == None)
      None
    else
      Some(d.get.asInstanceOf[authData])
  }
}
