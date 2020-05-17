package rep.authority

import rep.protos.peer.{Authorize, BindCertToAuthorize, Certificate, Operate, Signer, Transaction}
import rep.authority.cache.{AccountMgr, Authorizemgr, OperateMgr}
import scala.collection.mutable.{ArrayBuffer, HashMap}

class AuthorizesOfSigner(accountmgr: AccountMgr,opmgr:OperateMgr,authmgr:Authorizemgr) {
  var signer : Signer = null
  var ops: HashMap[String, Operate] = null

  def InitByHash(hash:String)={
    this.signer = accountmgr.getSignerByCertHash(hash)
    getAllOperate
  }

  def InitByCertid(certid:String) = {
    this.signer = accountmgr.getSignerByCertId(certid)
    getAllOperate
  }

  def isValid:Boolean={
    this.signer.signerValid
  }

  def isAuthById(opid:String): Boolean ={
    this.ops.contains(opid)
  }

  private def getAllOperate={
    this.getAuthOpFromSigner
    this.getOperateForSelf
  }

  private def getAuthOpFromSigner={
    val ls = this.signer.authorizeIds
    ls.foreach(f=>{
        var tmp = this.authmgr.getAuthById(f)
        if(tmp != null){
          if(tmp.authorizeValid){
            tmp.opId.foreach(fl=>{
              var op = this.opmgr.getOperateById(fl)
              if(op != null){
                if(op.opValid){
                  this.ops += op.opId -> op
                }
              }
            })
          }
        }
    })
  }

  private def getOperateForSelf={
    val ls = this.signer.operateIds
    ls.foreach(f=>{
      var op = this.opmgr.getOperateById(f)
      if(op != null){
        if(op.opValid){
          this.ops += op.opId -> op
        }
      }
    })
  }



}
