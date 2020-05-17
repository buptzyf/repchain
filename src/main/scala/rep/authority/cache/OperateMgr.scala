package rep.authority.cache

import rep.protos.peer.Operate
import scala.collection.mutable.HashMap

class OperateMgr(accountmgr: AccountMgr) {
  var operates: HashMap[String, Operate] = new HashMap[String, Operate]()

  private def addOperateByOpid(opid:String): Operate ={
    var result : Operate = null
    synchronized{
      try{
        if(!this.operates.contains(opid)){
          var op = this.accountmgr.getOperateByOpid(opid)
          if(op != null){
            this.operates += opid -> op
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

  private def isExistOpid(opid:String):Boolean={
    this.operates.contains(opid)
  }

  def getOperateById(opid:String):Operate={
    var result : Operate = null
    if(this.isExistOpid(opid)){
      result = this.operates(opid)
    }else{
      result = this.addOperateByOpid(opid)
    }
    result
  }
}
