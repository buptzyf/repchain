package rep.sc.tpl

import org.fisco.bcos.asset.crypto.elgamal.{ElGamalKeyPairGenerator, ElGamalPrivateKey, ElGamalPublicKey, ElGamal_Ciphertext}
import org.fisco.bcos.asset.crypto.zeroknowledgeproof.{LinearEquationProof, ZeroKonwledgeProofGorV}
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse
import org.json4s.jackson.Serialization.{write, writePretty}
import org.slf4j.Logger
import rep.proto.rc2.ActionResult
import rep.sc.scalax.{ContractContext, ContractException, IContract}

import java.io.{File, FileInputStream, FileOutputStream, ObjectInputStream, ObjectOutputStream}
import java.security.KeyPair
import scala.collection.mutable.HashMap

case class ZkpTask(id: String, members: Seq[String], taskType: String)

case class EncryptData(taskId: String, memberId: String, data: String)

case class VerifyProof(taskId: String, mId1: String, mId2: String, proofData: String)

class ZkpProof extends IContract {

  val encryptPrefix = "zkp-task-encrypt-"
  val taskPrefix = "zkp-task-key-"
  val proofResPrefix = "zkp-task-proof-key-"

  implicit val formats: DefaultFormats.type = DefaultFormats
  var logger: Logger = _

  def init(ctx: ContractContext): Unit = {
    this.logger = ctx.api.getLogger
    logger.info(s"init | 初始化零知识证明存证合约：${ctx.t.cid.get.chaincodeName}, 交易ID为：${ctx.t.id}")
  }

  /**
   * 读取publicKey
   *
   * @param fn
   * @return
   */
  private def getPublicKey(fn: String): ElGamalPublicKey = {
    var key: ElGamalPublicKey = null
    val pf = new File(fn + "_pub_key")
    var pin: ObjectInputStream = null
    try {
      pin = new ObjectInputStream(new FileInputStream(pf.getAbsolutePath))
      val e_pk = pin.readObject.asInstanceOf[ElGamalPublicKey]
      key = e_pk
    } catch {
      case e: Exception =>
        e.printStackTrace()
    } finally {
      if (pin != null) try pin.close()
      catch {
        case el: Exception =>
          el.printStackTrace()
      }
    }
    key
  }

  /**
   * 发布任务
   *
   * @param ctx
   * @param data
   * @return
   */
  def publishTask(ctx: ContractContext, data: String): ActionResult = {
    val task = parse(data).extract[ZkpTask]
    ctx.api.setVal(taskPrefix + task.id, data)
    ActionResult()
  }

  /**
   * 发布数据
   *
   * @param ctx
   * @param data
   * @return
   */
  def publishData(ctx: ContractContext, data: String): ActionResult = {
    val encryptData = parse(data).extract[EncryptData]
    ctx.api.setVal(encryptPrefix + encryptData.taskId + "-" + encryptData.memberId, data)
    ActionResult()
  }

  /**
   * 根据任务做对应的证明验证
   *
   * @param ctx
   * @param data
   * @return
   */
  def verifyProof(ctx: ContractContext, data: String): ActionResult = {
    val proof = new ZeroKonwledgeProofGorV
    val verifyProof = parse(data).extract[VerifyProof]
    val task = parse(ctx.api.getVal(taskPrefix + verifyProof.taskId).asInstanceOf[String]).extract[ZkpTask]
    if (task.taskType.contentEquals("equalproof")) {
      val data1 = parse(ctx.api.getVal(encryptPrefix + verifyProof.taskId + "-" + verifyProof.mId1).asInstanceOf[String]).extract[EncryptData]
      val data2 = parse(ctx.api.getVal(encryptPrefix + verifyProof.taskId + "-" + verifyProof.mId2).asInstanceOf[String]).extract[EncryptData]
      val key1 = getPublicKey(s"key/${verifyProof.mId1}")
      val key2 = getPublicKey(s"key/${verifyProof.mId2}")
      val proofRes = proof.VerifyEqualityProof(
        key1,
        key2,
        parse(data1.data).extract[ElGamal_Ciphertext],
        parse(data2.data).extract[ElGamal_Ciphertext],
        parse(verifyProof.proofData).extract[LinearEquationProof])
      if (proofRes)
        ctx.api.setVal(proofResPrefix + task.id, "0")
      else
        ctx.api.setVal(proofResPrefix + task.id, "1")
    }
    ActionResult()
  }

  def onAction(ctx: ContractContext, action: String, sdata: String): ActionResult = {
    action match {
      case "publishTask" => publishTask(ctx, sdata)
      case "publishData" => publishData(ctx, sdata)
      case "verifyProof" => verifyProof(ctx, sdata)
    }
  }

}
