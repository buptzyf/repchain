package rep.sc.tpl

import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse
import rep.proto.rc2.ActionResult
import rep.sc.scalax.{ContractContext, IContract}

class ConsensusParameterAdjust  extends IContract {
  override def init(ctx: ContractContext): Unit = {
    println(s"tid: ${ctx.t.id}, execute the contract which name is " +
      s"${ctx.t.getCid.chaincodeName} and version is ${ctx.t.getCid.version}")
  }

  /**
   * 共识参数调整
   * 1.背书策略：key:db-cfrd-endorsement-strategy value=2或者3 2：大于1/2；3：大于2/3
   * 2.出块大小：key:db-cfrd-block-size value=1024~5*1024*1024 单位是字节
   * 3.出块人的轮换频率：key:db-cfrd-blocker-rotation-frequency  value=1 最小轮换频率是1
   * */
  def parameterAdjustment(ctx: ContractContext,data: Map[String,String]):ActionResult = {
    data.keySet.foreach(key=>{
      val v = data(key)
      ctx.api.setVal(key, v)
    })
    null
  }

  override def onAction(ctx: ContractContext, action: String, sdata: String): ActionResult = {
    implicit val formats = DefaultFormats
    val json = parse(sdata)

    action match {
      case "parameterAdjustment" =>
        parameterAdjustment(ctx, json.extract[Map[String, String]])
    }
  }
}
