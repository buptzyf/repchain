package rep.sc.tpl

import rep.sc.contract._
import rep.protos.peer._
import org.json4s.jackson.JsonMethods._

import scala.collection.mutable.Map
import org.json4s.DefaultFormats
import rep.app.conf.SystemProfile
import rep.utils.{IdTool, SerializeUtils}

/**
  * @author zyf
  */
// 证书状态
case class CertStatus(credit_code: String, name: String, status: Boolean)
case class CertInfo(credit_code: String,name: String, cert: Certificate)

class ContractCert  extends IContract {
  implicit val formats = DefaultFormats

  val notNodeCert = "非管理员操作"
  val signerExists = "账户已存在"
  val signerNotExists = "账户不存在"
  val certExists = "证书已存在"
  val certNotExists = "证书不存在"
  val unknownError = "未知错误"
  val chaincodeName = SystemProfile.getAccountChaincodeName
  val chaincodeVersion = SystemProfile.getAccountChaincodeVersion
  //val prefix = IdTool.getCid(ChaincodeId(chaincodeName, chaincodeVersion))
  val underline = "_"
  val dot = "."
  // 锚点，错误回退
  var anchor: Map[String, Any] = Map()

  object ACTION {
    val SignUpSigner = "SignUpSigner"
    val SignUpCert = "SignUpCert"
    val UpdateCertStatus = "UpdateCertStatus"
    val UpdateSigner = "UpdateSigner"
  }

  

  /**
    * 注册Signer账户
    * @param ctx
    * @param data
    * @return
    */
  def signUpSigner(ctx: ContractContext, data:Signer):ActionResult = {
    val isNodeCert = ctx.api.bNodeCreditCode(ctx.t.getSignature.getCertId.creditCode)
    if (!isNodeCert) {
      return ActionResult(0,Some(notNodeCert))
    }
    // 存Signer账户
    //val signerKey = prefix + underline + data.creditCode
    val signer = ctx.api.getState(data.creditCode)
    // 如果是null，表示已注销，如果不是null，则判断是否有值
    if (signer == null || new String(signer).equalsIgnoreCase("null")){
      ctx.api.setVal(data.creditCode, data)
      ActionResult(1,None)
    } else {
      ActionResult(0,Some(signerExists))
    }
  }

  /**
    * 注册用户证书：1、将name加到账户中；2、将Certificate保存
    * @param ctx
    * @param data
    * @return
    */
  def signUpCert(ctx: ContractContext, data:CertInfo): ActionResult = {
    val isNodeCert = ctx.api.bNodeCreditCode(ctx.t.getSignature.getCertId.creditCode)
    if (!isNodeCert) {
      return ActionResult(0,Some(notNodeCert))
    }
    val certKey =  data.credit_code + dot + data.name
    val certInfo = ctx.api.getState(certKey)
    val signerKey =  data.credit_code
    val signerContent = ctx.api.getState(signerKey)
    // 先判断证书，若证书不存在，则向账户添加name
    if (certInfo == null || new String(certInfo).equalsIgnoreCase("null")) {
      if (signerContent == null || new String(signerContent).equalsIgnoreCase("null")){
        return ActionResult(0,Some(signerNotExists))
      } else {
        ctx.api.setVal(certKey, data.cert)
        val signer = SerializeUtils.deserialise(signerContent).asInstanceOf[Signer]
        if (!signer.certNames.contains(data.name)){
          signer.addCertNames(data.name)
          ctx.api.setVal(signerKey, signer)
        }
      }
      ActionResult(1, None)
    } else {
      ActionResult(0, Some(certExists))
    }
  }

  // TODO
  def rollback(map: Map[String, Byte]): Unit = {}

  /**
    * 用户证书禁用、启用
    * @param ctx
    * @param data
    * @return
    */
  def updateCertStatus(ctx: ContractContext, data: CertStatus): ActionResult = {
    val isNodeCert = ctx.api.bNodeCreditCode(ctx.t.getSignature.getCertId.creditCode)
    if (!isNodeCert) {
      return ActionResult(0,Some(notNodeCert))
    }
    val certKey =  data.credit_code + dot + data.name
    val certInfo = ctx.api.getState(certKey)
    if (certInfo == null || new String(certInfo).equalsIgnoreCase("null")) {
      ActionResult(0,Some(certNotExists))
    } else {
      val cert = SerializeUtils.deserialise(certInfo).asInstanceOf[Certificate]
      cert.withCertValid(data.status)
      ctx.api.setVal(certKey, cert)
      ActionResult(1,None)
    }
  }

  /**
    * 更新账户相关信息
    * @param ctx
    * @param data
    * @return
    */
  def updateSigner(ctx: ContractContext, data: Signer): ActionResult = {
    val isNodeCert = ctx.api.bNodeCreditCode(ctx.t.getSignature.getCertId.creditCode)
    if (!isNodeCert) {
      return ActionResult(0,Some(notNodeCert))
    }
    val signer = ctx.api.getState(data.creditCode)
    // 如果是null，账户不存在，不存在则不能更新
    if (signer == null || new String(signer).equalsIgnoreCase("null")){
      ActionResult(0,Some(signerNotExists))
    } else {
      ctx.api.setVal(data.creditCode, data)
      ActionResult(1,None)
    }
  }

  def init(ctx: ContractContext){
    println(s"tid: $ctx.t.txid")
  }


  /**
    * 合约方法入口
    */
  def onAction(ctx: ContractContext,action:String, sdata:String ): ActionResult={
    val json = parse(sdata)

    action match {
      case ACTION.SignUpSigner =>
        println("SignUpSigner")
        signUpSigner(ctx, json.extract[Signer])
      case ACTION.SignUpCert =>
        println("SignUpCert")
        signUpCert(ctx, json.extract[CertInfo])
      case ACTION.UpdateCertStatus =>
        println("UpdateCertStatus")
        updateCertStatus(ctx, json.extract[CertStatus])
      case ACTION.UpdateSigner =>
        println("UpdateSigner")
        updateSigner(ctx, json.extract[Signer])
    }
  }

}