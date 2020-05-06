package rep.sc.tpl.did.operation

import rep.protos.peer.{ActionResult, Certificate, Signer}
import rep.sc.scalax.{ContractContext, ContractException}
import rep.sc.tpl.did.operation.SignerOperation.stateNotMatchFunction

/**
  * @author zyf
  */
object CertOperation extends DidOperation {

  val isAuthCert = "被注册证书为身份校验证书，而非普通证书"
  val creditCodeNotMatch = "creditCode不匹配，交易签名者为%s，参数证书为%s"
  val customCertExists = "证书已存在"
  val certNotExists = "证书不存在"

  case class CertStatus(creditCode: String, certName: String, state: Boolean)

  /**
    * 判断签名是否对应为身份证书，以及检查如果是身份证书，是否是在操作自己的证书
    *
    * @param ctx
    * @param customCert
    * @return
    */
  def checkAuthCertAndRule(ctx: ContractContext, customCert: Certificate): Boolean = {
    val tranCertId = ctx.t.getSignature.getCertId
    val tranCert = ctx.api.getVal(tranCertId.creditCode + "_" + tranCertId.certName).asInstanceOf[Certificate]
    if (!tranCert.certType.isCertAuthentication) {
      throw ContractException(notAuthCert)
    } else if (!tranCert.getId.creditCode.equals(customCert.getId.creditCode)) {
      // 需要是同一账户下的身份证书与普通证书
      throw ContractException(creditCodeNotMatch.format(tranCert.getId.creditCode, customCert.getId.creditCode))
    } else {
      tranCert.certType.isCertAuthentication
    }
  }

  /**
    * 只允许注册普通证书
    *
    * @param ctx
    * @param customCert
    * @return
    */
  def signUpCertificate(ctx: ContractContext, customCert: Certificate): ActionResult = {
    checkChainCert(ctx) || checkAuthCertAndRule(ctx, customCert)
    val customCertId = customCert.getId
    // 检查账户的有效性
    checkSignerValid(ctx, customCertId.creditCode)
    val certKey = customCertId.creditCode + "_" + customCertId.certName
    if (ctx.api.getVal(certKey) == null) {
      if (customCert.certType.isCertCustom) {
        ctx.api.setVal(certKey, customCert)
      } else {
        throw ContractException(isAuthCert)
      }
    } else {
      throw ContractException(customCertExists)
    }
    null
  }

  /**
    * 禁用证书，身份证书应该也可以被禁用
    *
    * @param ctx
    * @param status
    * @return
    */
  def disableCertificate(ctx: ContractContext, status: CertStatus): ActionResult = {
    if (status.state) {
      throw ContractException(stateNotMatchFunction)
    } else {
      // 检查账户的有效性
      checkSignerValid(ctx, status.creditCode)
      val certKey = status.creditCode + "_" + status.certName
      val oldCert = ctx.api.getVal(certKey)
      if (oldCert != null) {
        val cert = oldCert.asInstanceOf[Certificate]
        checkChainCert(ctx) || checkAuthCertAndRule(ctx, cert)
        val disableTime = ctx.t.getSignature.getTmLocal
        val newCert = cert.withCertValid(status.state).withUnregTime(disableTime)
        ctx.api.setVal(certKey, newCert)
        // 如果是身份证书，则将Signer中的身份证书列表更新
        if (newCert.certType.isCertAuthentication && checkChainCert(ctx)) {
          val signer = ctx.api.getVal(status.creditCode).asInstanceOf[Signer]
          val newAuthCerts = signer.authenticationCerts.filterNot(cert => cert.certHash.equals(newCert.certHash)).:+(newCert)
          val newSigner = signer.withAuthenticationCerts(newAuthCerts)
          ctx.api.setVal(status.creditCode, newSigner)
        }
      } else {
        throw ContractException(certNotExists)
      }
    }
    null
  }

  /**
    *
    * @param ctx
    * @param cert
    * @return
    */
  def enableCertificate(ctx: ContractContext, status: CertStatus): ActionResult = {
    // TODO
    null
  }

}
