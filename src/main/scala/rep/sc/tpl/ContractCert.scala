package rep.sc.tpl

import java.io.{ByteArrayInputStream, StringReader}
import java.security.cert.{CertificateFactory, X509Certificate}

import org.bouncycastle.util.io.pem.PemReader

import rep.sc.contract._
import rep.protos.peer._
import org.json4s.jackson.JsonMethods._
import scala.collection.mutable.Map
import org.json4s.{DefaultFormats, Formats, jackson}

import org.json4s.DefaultFormats
import rep.app.conf.SystemProfile
import rep.utils.SerializeUtils

/**
  * @author zyf
  */
class ContractCert  extends IContract {
  implicit val formats = DefaultFormats

  val notNodeCert = "非管理员操作"
  val signerExists = "账户已存在"
  val signerNotExists = "账户不存在"
  val certExists = "证书已存在"
  val certNotExists = "证书不存在"
  val unknownError = "未知错误"
  val prefix = SystemProfile.getAccountChaincodeName
  val underline = "_"
  val dot = "."
  // 锚点，错误回退
  var anchor: Map[String, Any] = Map()

  object ACTION {
    val SignUpSigner = "SignUpSigner"
    val SignUpCert = "SignUpCert"
    val UpdateCertStatus = "UpdateCertStatus"
  }

  // 证书状态
  case class CertStatus(credit_code: String, name: String, status: Boolean)
  case class CertInfo(credit_code: String,name: String, cert: Certificate)

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
    val signerKey = prefix + underline + data.creditCode
    val signer = ctx.api.getState(signerKey)
    // 如果是null，表示已注销，如果不是null，则判断是否有值
    if (signer == null || new String(signer).equalsIgnoreCase("null")){
      ctx.api.setVal(signerKey, data)
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
    val certKey = prefix + underline + data.credit_code + dot + data.name
    val certInfo = ctx.api.getState(certKey)
    val signerKey = prefix + underline + data.credit_code
    val signerContent = ctx.api.getState(signerKey)
    // 先判断证书，若证书不存在，则向账户添加name
    if (certInfo == null || new String(certInfo).equalsIgnoreCase("null")) {
      val certificate = generateX509Cert(data.cert.certificate)
      if (certificate.isEmpty) {
        return ActionResult(0, Some(unknownError))
      }
      if (signerContent == null || new String(signerContent).equalsIgnoreCase("null")){
        return ActionResult(0,Some(signerNotExists))
      } else {
        ctx.api.setVal(certKey, certificate.get)
        val signer = SerializeUtils.deserialise(signerContent).asInstanceOf[Signer]
        if (!signer.certNames.contains(data.name))
          signer.addCertNames(data.name)
          ctx.api.setVal(signerKey, signer)
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
    val certKey = prefix + underline + data.credit_code + dot + data.name
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
    * 根据pem字符串生成证书
    * @param certPem
    * @return
    */
  def generateX509Cert(certPem: String): Option[X509Certificate] = {
    try {
      val cf = CertificateFactory.getInstance("X.509")
      val pemReader = new PemReader(new StringReader(certPem))
      val certByte = pemReader.readPemObject().getContent()
      val x509Cert = cf.generateCertificate(new ByteArrayInputStream(certByte))
      Some(x509Cert.asInstanceOf[X509Certificate])
    } catch {
      case ex: Exception =>
        None
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
    }
  }

}