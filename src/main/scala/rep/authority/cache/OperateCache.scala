package rep.authority.cache

import rep.app.system.RepChainSystemContext
import rep.proto.rc2.Operate
import rep.proto.rc2.Operate.OperateType
import rep.sc.tpl.did.DidTplPrefix
import rep.storage.chain.preload.BlockPreload

object OperateCache{
  case class opData(opId:String,opValid:Boolean,isOpen:Boolean,register:String)
}

class OperateCache(ctx : RepChainSystemContext) extends ICache(ctx) {
  import OperateCache._

  override protected def dataTypeConvert(any: Option[Any],blockPreload: BlockPreload): Option[Any] = {
    if(any == None){
      None
    }else{
      var od : Option[opData] = None
      if(any != None) {
        val op = any.get.asInstanceOf[Operate]
        if (op.operateType == OperateType.OPERATE_CONTRACT) {
          od = Some(opData(op.opId, op.opValid, op.isPublish,op.register))
        } else if (op.operateType == OperateType.OPERATE_SERVICE) {
          if (!op.operateServiceName.isEmpty) {
            od = Some(opData(op.opId,  op.opValid, op.isPublish,op.register))
          }
        }
      }
      od
    }
  }

  override protected def getPrefix: String = {
    this.common_prefix + this.splitSign + DidTplPrefix.operPrefix
  }

  def get(key:String,blockPreload: BlockPreload):Option[opData]={
    val d = this.getData(key,blockPreload)
    if(d == None)
      None
    else
      Some(d.get.asInstanceOf[opData])
  }
}
