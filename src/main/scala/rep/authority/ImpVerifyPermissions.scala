package rep.authority

import rep.authority.MsgOfVerifyPermission.ResultOfVerify
import rep.authority.cache.{AccountMgr, Authorizemgr, OperateMgr}
import rep.crypto.Sha256


class ImpVerifyPermissions(sysTag:String) extends IVerifyPermissions {
  var accountmgr = new AccountMgr(sysTag)
  var opmgr = new OperateMgr(accountmgr)
  var authmgr = new Authorizemgr(accountmgr)

  /**
   * 参数列表：
   * operate_endpoint //服务器地址
   * operate_service_name//服务名
   * cert_hash//证书的sha256
   **/
  override def VerifyOfService(operate_endpoint: String, operate_service_name: String, cert_hash: String): ResultOfVerify = {
    var result:ResultOfVerify = null
    if(!CertValidByHash(cert_hash)){
      result = new ResultOfVerify(false,MsgTypeOfVerify.CertInvalid)
    }else{
      var sig = new AuthorizesOfSigner(this.accountmgr,this.opmgr,this.authmgr)
      sig.InitByHash(cert_hash)
      if(!sig.isValid){
        result = new ResultOfVerify(false,MsgTypeOfVerify.ForbidedOfSigner)
      }else{
        if(sig.isAuthById(Sha256.hashstr(operate_endpoint+"."+operate_service_name))){
          result = new ResultOfVerify(true,MsgTypeOfVerify.Success)
        }else{
          result = new ResultOfVerify(false,MsgTypeOfVerify.NotPermission)
        }
      }
    }
    result
  }

  private def CertValidByHash(cert_hash:String):Boolean = {
    var result :Boolean  = false
    val cert = accountmgr.getCertInfoByCertHash(cert_hash)
    if(cert != null){
      result = cert.certValid
    }
    result
  }

  private def CertValidByCertid(certid:String):Boolean = {
    var result :Boolean  = false
    val cert = accountmgr.getCertInfoByCertId(certid)
    if(cert != null){
      result = cert.certValid
    }
    result
  }

  override def VerifyOfContract(operate_endpoint: String, operate_service_name: String, auth_full_name: String, certid: String): ResultOfVerify = {
    var result:ResultOfVerify = null
    if(!CertValidByCertid(certid)){
      result = new ResultOfVerify(false,MsgTypeOfVerify.CertInvalid)
    }else{
      var sig = new AuthorizesOfSigner(this.accountmgr,this.opmgr,this.authmgr)
      sig.InitByCertid(certid)
      if(!sig.isValid){
        result = new ResultOfVerify(false,MsgTypeOfVerify.ForbidedOfSigner)
      }else{
        if(sig.isAuthById(Sha256.hashstr(operate_endpoint+"."+operate_service_name+"."+auth_full_name))){
          result = new ResultOfVerify(true,MsgTypeOfVerify.Success)
        }else{
          result = new ResultOfVerify(false,MsgTypeOfVerify.NotPermission)
        }
      }
    }
    result
  }
}
