package rep.sc.tpl

import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse
import org.json4s.jackson.Serialization.{write, writePretty}
import org.slf4j.Logger
import rep.crypto.group.{GroupAdminPublicKey, GroupKeyFactory, GroupSignature, GroupSignatureResult}
import rep.proto.rc2.ActionResult
import rep.sc.scalax.{ContractContext, ContractException, IContract}

import scala.collection.mutable.HashMap

case class Vote(id: String, memberId: String, message: String, signatureResult: String)

class VoteProof extends IContract {

  val adminKeyPath = "group_true/admin-public"
  val votePrefix = "group-vote-key-"

  implicit val formats: DefaultFormats.type = DefaultFormats
  var logger: Logger = _

  def init(ctx: ContractContext): Unit = {
    this.logger = ctx.api.getLogger
    logger.info(s"init | 初始化投票存证合约：${ctx.t.cid.get.chaincodeName}, 交易ID为：${ctx.t.id}")
  }

  /**
   * 读取管理员秘钥
   *
   * @return
   */
  def readAdminKey(): GroupAdminPublicKey = {
    val gf = new GroupKeyFactory("f")
    val pubKey = new GroupAdminPublicKey(gf.getPairing, adminKeyPath)
    pubKey
  }

  /**
   * 验证群签名
   *
   * @param signatureResultStr
   * @param message
   * @param adminPubKey
   * @return
   */
  def verifySign(signatureResultStr: String, message: String, adminPubKey: GroupAdminPublicKey): Boolean = {
    val gs = new GroupSignature("f")
    val gr = new GroupSignatureResult(gs.getPairing, signatureResultStr)
    val vr = gs.VerifyGroupSignature(message, gr, adminPubKey)
    vr
  }

  /**
   *
   * @param ctx
   * @param data
   * @return
   */
  def vote(ctx: ContractContext, data: String): ActionResult = {
    val vote = parse(data).extract[Vote]
    val voteKey = votePrefix + vote.id + "-" + vote.memberId
    val adminKey = readAdminKey()
    val res = verifySign(vote.signatureResult, vote.message, adminKey)
    if (!res) {
      ctx.api.getLogger.error(s"验证失败, 非群成员: ${data}")
      throw ContractException("验证失败, 非群成员")
    } else {
      ctx.api.setVal(voteKey, data)
    }
    ActionResult()
  }

  def onAction(ctx: ContractContext, action: String, sdata: String): ActionResult = {
    action match {
      case "vote" => vote(ctx, sdata)
    }
  }

}
