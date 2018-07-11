package rep.sc.tpl

import org.json4s.jackson.JsonMethods.parse
import rep.sc.contract.{ContractContext, IContract}

class ReCharge extends IContract{
  def init(ctx: ContractContext){
    println(s"tid: $ctx.t.txid")
  }

  def reCharge( ): Unit = {

  }

  def onAction(ctx: ContractContext,action:String, sdata:String ):Object={
    val json = parse(sdata)
  }
}
