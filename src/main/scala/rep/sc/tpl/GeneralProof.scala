package rep.sc.tpl

import org.json4s
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse
import org.json4s.jackson.Serialization.write
import org.slf4j.Logger
import rep.proto.rc2.ActionResult
import rep.sc.scalax.{ContractContext, ContractException, IContract}

import scala.collection.immutable.Map

case class Template(ledgerCallConfId: String, templateDesp: Map[String, Any])
case class LedgerData(ledgerCallConfId: String, uuid: String, data: Map[String, Any])

class GeneralProof extends IContract{

  val tempPrefix = "template-"
  val ledgerPrefix = "ledger-"

  implicit val formats: json4s.DefaultFormats.type = DefaultFormats
  var logger: Logger = _

  def init(ctx: ContractContext): Unit = {
    this.logger = ctx.api.getLogger
    logger.info(s"init | 初始化通用存证合约：${ctx.t.cid.get.chaincodeName}, 交易ID为：${ctx.t.id}")
  }

  /**
   * 注册账本模板
   *
   * @param ctx
   * @param data
   * @return
   */
  def registerTemplate(ctx: ContractContext, data: String): ActionResult = {
    val temp = parse(data).extract[Template]
    if (ctx.api.getVal(temp.ledgerCallConfId) != null) {
      throw ContractException("账本模板已经存在，请使用'updateTemplate'进行更新")
    }
    ctx.api.setVal(tempPrefix + temp.ledgerCallConfId, data)
    ActionResult()
  }

  /**
   * 更新账本模板
   *
   * @param ctx
   * @param data
   * @return
   */
  def updateTemplate(ctx: ContractContext, data: String): ActionResult = {
    val temp = parse(data).extract[Template]
    ctx.api.setVal(tempPrefix + temp.ledgerCallConfId, data)
    ActionResult()
  }

  /**
   * 存证数据
   *
   * @param ctx
   * @param data
   * @return
   */
  def proofLedgerData(ctx: ContractContext, data: String): ActionResult = {
    val ledgerDataList = parse(data).extract[List[LedgerData]]
    ledgerDataList.foreach(ledgerData => {
      val temp = parse(ctx.api.getVal(tempPrefix + ledgerData.ledgerCallConfId).asInstanceOf[String]).extract[Template]
      if (temp != null) {
        val mainFieldTemp = temp.templateDesp
        val fieldDespList = mainFieldTemp("fieldDespList").asInstanceOf[List[Map[String, Any]]]
        val fieldData = ledgerData.data
        if (fieldData.size > fieldDespList.size) {
          throw ContractException(s"账本字段数${fieldData.size}超出了模板定义的字段数${fieldDespList.size}")
        }

        fieldData.foreach(elem => {
          val fieldKey = elem._1
          if (!fieldDespList.exists(fieldDesp => fieldDesp("FORMAT_FIELD").asInstanceOf[String].contentEquals(fieldKey))) {
            throw ContractException(s"模板中不存在该字段${fieldKey}")
          }
        })

        for (fieldDesp <- fieldDespList) {
          fieldDesp("IS_NULL") match {
            case "Y" =>
            case "N" =>
              if (!fieldData.exists(field => field._1.contentEquals(fieldDesp("FORMAT_FIELD").asInstanceOf[String]))) {
                // 如果不是文件
                if (fieldDesp("DATA_TYPE").asInstanceOf[BigInt] != 4) {
                  throw ContractException(s"字段${fieldDesp("FORMAT_FIELD").asInstanceOf[String]}模板设置为非空, 但是数据不存在该字段")
                }
              }
          }
        }
      } else {
        throw ContractException(s"对应的模板id为${ledgerData.ledgerCallConfId}的模板不存在")
      }
      ctx.api.setVal(ledgerPrefix + ledgerData.uuid, write(ledgerData))
    })
    ActionResult()
  }

  def onAction(ctx: ContractContext, action: String, sdata: String): ActionResult = {
    action match {
      case "registerTemplate" => registerTemplate(ctx, sdata)
      case "updateTemplate" => updateTemplate(ctx, sdata)
      case "proofLedgerData" => proofLedgerData(ctx, sdata)
      case _ => throw ContractException("该合约没有改方法")
    }
  }

}
