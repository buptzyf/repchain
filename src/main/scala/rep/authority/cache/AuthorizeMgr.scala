package rep.authority.cache

import rep.protos.peer.Authorize
import scala.collection.mutable.HashMap

class Authorizemgr (accountmgr: AccountMgr) {
  var auths: HashMap[String, Authorize] = new HashMap[String, Authorize]()

  private def addAuthorizeByAuthid(authid:String): Authorize ={
    var result : Authorize = null
    synchronized{
      try{
        if(!this.auths.contains(authid)){
          var op = this.accountmgr.getAuthorizeByAuthId(authid)
          if(op != null){
            this.auths += authid -> op
            result = op
          }
        }
      }catch {
        case e:Exception =>{
          //todo add error to log file
        }
      }
    }
    result
  }

  private def isExistAuthid(authid:String):Boolean={
    this.auths.contains(authid)
  }

  def getAuthById(authid:String):Authorize={
    var result : Authorize = null
    if(this.isExistAuthid(authid)){
      result = this.auths(authid)
    }else{
      result = this.addAuthorizeByAuthid(authid)
    }
    result
  }
}