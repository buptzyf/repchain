package rep.sc.tpl

import java.io.StringReader
import org.bouncycastle.util.io.pem.PemReader
import org.json4s._
import org.json4s.jackson.JsonMethods._
import rep.proto.rc2.ActionResult
import rep.sc.scalax.{ContractContext, ContractException, IContract}
import scala.collection.mutable.HashMap


/**
 * @author zyf
 */
class ManageNodeCert extends IContract {

  val key_trust_stores = "TSDb-Trust-Stores"

  def init(ctx: ContractContext) {
    println(s"tid: ${ctx.t.id}, execute the contract which name is ${ctx.t.getCid.chaincodeName} and version is ${ctx.t.getCid.version}")
  }

  /**
   *
   * @param ctx
   * @param data (节点名 -> 证书pem字符串)，如果是(节点名 -> "")，则移除该节点的证书
   * @return
   */
  def updateNodeCert(ctx: ContractContext, data: Map[String, String]): ActionResult = {
    if (ctx.api.getVal(key_trust_stores) == null) {
      throw ContractException("未初始化")
    }
    val certMap = ctx.api.getVal(key_trust_stores).asInstanceOf[HashMap[String, Array[Byte]]]
    for ((alias, certPem) <- data) {
      if (certPem.equals("")) {
        certMap.remove(alias)
      } else {
        val pemReader = new PemReader(new StringReader(certPem))
        val certBytes = pemReader.readPemObject().getContent
        certMap.put(alias, certBytes)
      }
    }
    ctx.api.setVal(key_trust_stores, certMap)
    null
  }

  /**
   * 根据action,找到对应的method，并将传入的json字符串parse为method需要的传入参数
   */
  def onAction(ctx: ContractContext, action: String, sdata: String): ActionResult = {

    implicit val formats = DefaultFormats
    val json = parse(sdata)

    action match {
      case "updateNodeCert" =>
        updateNodeCert(ctx, json.extract[Map[String, String]])
    }
  }

}