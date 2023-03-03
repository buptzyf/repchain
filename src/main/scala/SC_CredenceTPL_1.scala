

import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.slf4j.Logger
import rep.proto.rc2.ActionResult
import rep.sc.scalax.{ContractContext, ContractException, IContract}

/**
 * 信用数据上链
 */

case class SC_CredenceTPL_1CredenceData(uuid: String, data: String)

class SC_CredenceTPL_1 extends IContract{

  private var logger: Logger = _
  private implicit val formats = DefaultFormats

  def init(ctx: ContractContext): Unit = {
    this.logger = ctx.api.getLogger
    logger.info(s"tid: ${ctx.t.id}")
  }

  /**
   * 存证信用数据
   *
   * @param ctx
   * @param creData
   * @return
   */
  def creProof(ctx: ContractContext, creData:SC_CredenceTPL_1CredenceData): ActionResult = {
    if (ctx.api.getVal(creData.uuid) != null) {
      throw ContractException(s"uuid: ${creData.uuid} is exist")
    }
    ctx.api.setVal(creData.uuid, creData.data)
    logger.info(s"存证数据, uuid: ${creData.uuid}, data: {creData.data}")
    null
  }

  /**
   * 根据action,找到对应的method，并将传入的json字符串parse为method需要的传入参数
   */
  def onAction(ctx: ContractContext, action: String, sdata: String): ActionResult = {
    val json = parse(sdata)
    try {
      action match {
        case "creProof" => creProof(ctx, json.extract[SC_CredenceTPL_1CredenceData])
        case _ => throw ContractException("no such method")
      }
    } catch {
      case ex: MappingException => throw ContractException(ex.getMessage)
    }
  }

}
