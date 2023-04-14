import org.json4s._
import org.json4s.jackson.JsonMethods._
import rep.proto.rc2.ActionResult
import rep.sc.scalax.{ContractContext, IContract}

/**
 *
 * @param height        被审查区块的高度
 * @param blockHash     被审查区块的Hash
 * @param illegalTrans 违规交易Map, key:tixd, value:违规原因。
 */
final case class Conclusion(height: Long, blockHash: String, illegalTrans: Map[String, String])

class RegulateTPL extends IContract {
  def init(ctx: ContractContext): Unit = {
    println(s"tid: ${ctx.t.id}, contract: ${ctx.t.getCid.chaincodeName}, version: ${ctx.t.getCid.version}")
  }

  /**
   * @description: 交易监管, 把区块也做一个标记，这样就可以先判断区块中是否有违规的交易，如果有找出违规交易，否则不再遍历交易
   * @param conslusions 监管结论数组
   * @date: 2023/4/7
   * @version: 1.0
   */
  def regBlocks(ctx: ContractContext, conslusions: Seq[Conclusion]): ActionResult = {
    conslusions.foreach(c => {
      for ((txid, code) <- c.illegalTrans) {
        ctx.api.setVal(txid, code)
      }
      ctx.api.setVal(c.blockHash + "_" + c.height, true)
    })
    null
  }

  def onAction(ctx: ContractContext, action: String, sdata: String): ActionResult = {

    implicit val formats = DefaultFormats
    val json = parse(sdata)

    action match {
      case "regBlocks" =>
        regBlocks(ctx, json.extract[Seq[Conclusion]])
    }
  }
}