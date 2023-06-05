
package rep.sc.tpl

import org.json4s._
import org.json4s.jackson.JsonMethods._
import rep.proto.rc2.{ActionResult, ChaincodeId}
import rep.sc.scalax.IContract
import rep.sc.scalax.ContractContext
import rep.sc.scalax.ContractException
import org.json4s.native.Serialization.write
import org.json4s.{DefaultFormats, jackson}
import org.slf4j.Logger
import paillier.PaillierCipher
import rep.utils.SerializeUtils

import scala.collection.immutable.HashMap
import scala.collection.mutable

/**
 * 资产管理合约
 */

final case class PaillierTransfer(from: String, min: String, to: String, add: String)

class PaillierAssets extends IContract {

  implicit val formats: DefaultFormats.type = DefaultFormats
  var logger: Logger = _

  def init(ctx: ContractContext) {
    this.logger = ctx.api.getLogger
    logger.info(s"init | 初始化同态加密转账合约：${ctx.t.cid.get.chaincodeName}, 交易ID为：${ctx.t.id}")
  }

  /**
   * 初始化
   *
   * @param ctx
   * @param data
   * @return
   */
  def set(ctx: ContractContext, data: Map[String, String]): ActionResult = {
    println(s"set data:$data")
    logger.info("账户赋初值: ", write(data))
    for ((k, v) <- data) {
      logger.info(s"账户: $k, 赋初值: $v")
      ctx.api.setVal(k, v)
    }
    null
  }

  /**
   * 加密金额的加减
   *
   * @param ctx
   * @param data
   * @return
   */
  def transfer(ctx: ContractContext, data: PaillierTransfer): ActionResult = {
    logger.info("为对应账户做转账: ", write(data))
    val dfrom = ctx.api.getVal(data.from).asInstanceOf[String]
    val dfromRemain = PaillierCipher.ciphertextAdd(dfrom, data.min)
    logger.info("初值: {}, 减的: {}, 最终: {}", dfrom, data.min, dfromRemain)
    ctx.api.setVal(data.from, dfromRemain)
    val dto = ctx.api.getVal(data.to).asInstanceOf[String]
    val dtoRemain = PaillierCipher.ciphertextAdd(dto, data.add)
    logger.info("初值: {}, 加的: {}, 最终: {}", dto, data.add, dtoRemain)
    ctx.api.setVal(data.to, dtoRemain)
    null
  }

  /**
   * 根据action,找到对应的method，并将传入的json字符串parse为method需要的传入参数
   */
  def onAction(ctx: ContractContext, action: String, sdata: String): ActionResult = {
    val json = parse(sdata)
    action match {
      case "transfer" =>
        transfer(ctx, json.extract[PaillierTransfer])
      case "set" =>
        set(ctx, json.extract[Map[String, String]])
    }
  }

}
