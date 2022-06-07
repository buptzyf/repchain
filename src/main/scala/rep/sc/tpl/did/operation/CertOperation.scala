package rep.sc.tpl.did.operation

import rep.crypto.Sha256
import rep.proto.rc2.{ActionResult, Certificate, Signer}
import rep.sc.scalax.{ContractContext, ContractException}
import rep.sc.tpl.did.DidTplPrefix.{certPrefix, hashPrefix, signerPrefix}
import rep.utils.IdTool

/**
  * 无需授权：自己注册普通证书，修改普通证书状态
  * 需要授权：可以为别人注册身份或普通证书，修改身份或普通证书状态
  *
  * @author zyf
  */
object CertOperation extends DidOperation {

  val canNotSignUpAuthCertificate = ActionResult(13001, "该方法不能被注册身份校验证书，请通过 signUpAllTypeCertificate")
  val creditCodeNotMatchCode = 13002
  val creditCodeNotMatch = "creditCode不匹配，交易签名者为%s，参数证书为%s"
  val posterNotAuthCert = ActionResult(13003, "交易提交者非身份校验证书")
  val customCertExists = ActionResult(13004, "证书已存在")
  val certNotExists = ActionResult(13005, "证书不存在")
  val notAdmin = ActionResult(13006, "非super_admin不能为super_admin注册certificate或修改certificate状态")
  val certExists = ActionResult(13007, "用户的身份证书或者普通证书已存在")
  val hashNotMatch = ActionResult(13008, "Certificate中hash字段与certificate字段计算得到的Hash不相等")
  val canNotOperateAuthCertificate = ActionResult(13009, "该方法不能修改身份证书状态，请通过 updateAllTypeCertificateStatus")

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
    val tranCert = ctx.api.getVal(certPrefix + tranCertId.creditCode + "." + tranCertId.certName).asInstanceOf[Certificate]
    if (!tranCert.certType.isCertAuthentication) {
      throw ContractException(toJsonErrMsg(posterNotAuthCert))
    } else if (!tranCert.getId.creditCode.equals(customCert.getId.creditCode)) {
      // 需要是同一账户下的身份证书与普通证书
      throw ContractException(toJsonErrMsg(creditCodeNotMatchCode, creditCodeNotMatch.format(tranCert.getId.creditCode, customCert.getId.creditCode)))
    } else {
      tranCert.certType.isCertAuthentication
    }
  }

  /**
    * 公开，无需授权，任何用户都可以使用自己的身份证书来为自己注册普通证书
    *
    * @param ctx
    * @param customCert
    * @return
    */
  def signUpCertificate(ctx: ContractContext, customCert: Certificate): ActionResult = {
    // 身份证书可以来注册普通证书
    checkAuthCertAndRule(ctx, customCert)
    val customCertId = customCert.getId
    // 检查账户的有效性，其实也是交易提交账户的有效性（如果验签时候校验了，此处可以不用校验）
    val signer = checkSignerValid(ctx, customCertId.creditCode)
    val certKey = certPrefix + customCertId.creditCode + "." + customCertId.certName
    val certHashKey = hashPrefix + customCert.certHash
    if (ctx.api.getVal(certKey) != null || ctx.api.getVal(certHashKey) != null) {
      throw ContractException(toJsonErrMsg(customCertExists))
    } else if (customCert.certType.isCertAuthentication || customCert.certType.isCertUndefined) {
      // 身份校验证书通过signer注册指定，或通过 signUpAllTypeCertificate
      throw ContractException(toJsonErrMsg(canNotSignUpAuthCertificate))
    } else if (!ctx.api.getSha256Tool.hashstr(IdTool.deleteLine(customCert.certificate)).equals(customCert.certHash)) {
      throw ContractException(toJsonErrMsg(hashNotMatch))
    } else {
      ctx.api.setVal(certKey, customCert)
      //设置证书的hash与证书的key对应关系
      ctx.api.setVal(certHashKey, customCertId.creditCode + "." + customCertId.certName)
      // 更新signer的certNames列表，存放的为"${did}.${certName}"
      val newSinger = signer.withCertNames(signer.certNames.:+(customCertId.creditCode + "." + customCertId.certName))
      ctx.api.setVal(signerPrefix + customCertId.creditCode, newSinger)
    }
    null
  }

  /**
    * 公开，无需授权，使用身份证书对应的签名交易，可以禁用普通证书
    *
    * @param ctx
    * @param status
    * @return
    */
  def updateCertificateStatus(ctx: ContractContext, status: CertStatus): ActionResult = {
    // 检查账户的有效性
    // checkSignerValid(ctx, status.creditCode)
    val certKey = certPrefix + status.creditCode + "." + status.certName
    val oldCert = ctx.api.getVal(certKey)
    if (oldCert != null) {
      val cert = oldCert.asInstanceOf[Certificate]
      // 身份证书，可以来禁用该账户的普通证书
      checkAuthCertAndRule(ctx, cert)
      // 不能修改身份证书的状态，通过 updateAllTypeCertificateStatus
      if (!cert.certType.isCertAuthentication) {
        var newCert = Certificate.defaultInstance
        if (status.state) {
          newCert = cert.withCertValid(status.state).clearUnregTime
        } else {
          val disableTime = ctx.t.getSignature.getTmLocal
          newCert = cert.withCertValid(status.state).withUnregTime(disableTime)
        }
        ctx.api.setVal(certKey, newCert)
      } else {
        throw ContractException(toJsonErrMsg(canNotOperateAuthCertificate))
      }
    } else {
      throw ContractException(toJsonErrMsg(certNotExists))
    }
    null
  }

  /**
    * 不公开，需要授权，可注册所有类型证书，如：身份证书或者普通证书，且可以为其他用户注册
    * 权限比较高，一般不授予出去
    *
    * @return
    */
  def signUpAllTypeCertificate(ctx: ContractContext, customCert: Certificate): ActionResult = {
    val customCertId = customCert.getId
    // 只有super_admin才能为super_admin注册证书
    if (ctx.api.isAdminCert(customCertId.creditCode) && ctx.t.getSignature.getCertId.creditCode != customCertId.creditCode) {
      throw ContractException(toJsonErrMsg(notAdmin))
    }
    // 检查账户的有效性，其实也是交易提交账户的有效性（如果验签时候校验了，此处可以不用校验）
    val signer = checkSignerValid(ctx, customCertId.creditCode)
    val certKey = certPrefix + customCertId.creditCode + "." + customCertId.certName
    val certHashKey = hashPrefix + customCert.certHash
    if (ctx.api.getVal(certKey) != null || ctx.api.getVal(certHashKey) != null) {
      throw ContractException(toJsonErrMsg(certExists))
    } else if (!ctx.api.getSha256Tool.hashstr(IdTool.deleteLine(customCert.certificate)).equals(customCert.certHash)) {
      throw ContractException(toJsonErrMsg(hashNotMatch))
    } else {
      ctx.api.setVal(certKey, customCert)
      //设置证书的hash与证书的key对应关系
      ctx.api.setVal(certHashKey, customCertId.creditCode + "." + customCertId.certName)
      // 更新signer的certNames列表，存放的为"${did}.${certName}"
      val newSigner = signer.withCertNames(signer.certNames.:+(customCertId.creditCode + "." + customCertId.certName))
      if (customCert.certType.isCertAuthentication) {
        val newSigner_1 = newSigner.withAuthenticationCerts(newSigner.authenticationCerts.:+(customCert))
        ctx.api.setVal(signerPrefix + customCertId.creditCode, newSigner_1)
      } else if (customCert.certType.isCertCustom) {
        ctx.api.setVal(signerPrefix + customCertId.creditCode, newSigner)
      } else {
        ctx.api.getLogger.info("undefined certificate")
      }
    }
    null
  }

  /**
    * 需要授权，修改证书（身份证书或者普通证书）状态，不可以修改super_admin的证书
    * 权限比较高，一般不授予出去
    *
    * @return
    */
  def updateAllTypeCertificateStatus(ctx: ContractContext, status: CertStatus): ActionResult = {
    // 普通用户不能修改super_admin的证书状态
    if (ctx.api.isAdminCert(status.creditCode) && ctx.t.getSignature.getCertId.creditCode != status.creditCode) {
      throw ContractException(toJsonErrMsg(notAdmin))
    }
    // 检查账户的有效性
    checkSignerValid(ctx, status.creditCode)
    val certKey = certPrefix + status.creditCode + "." + status.certName
    val oldCert = ctx.api.getVal(certKey)
    if (oldCert != null) {
      val cert = oldCert.asInstanceOf[Certificate]
      var newCert = Certificate.defaultInstance
      if (status.state) {
        newCert = cert.withCertValid(status.state).clearUnregTime
      } else {
        val disableTime = ctx.t.getSignature.getTmLocal
        newCert = cert.withCertValid(status.state).withUnregTime(disableTime)
      }
      ctx.api.setVal(certKey, newCert)
      // 如果是身份证书，则将Signer中的身份证书列表更新，身份证书可以禁用身份证书
      if (newCert.certType.isCertAuthentication) {
        val signer = ctx.api.getVal(signerPrefix + status.creditCode).asInstanceOf[Signer]
        val newAuthCerts = signer.authenticationCerts.filterNot(cert => cert.certHash.equals(newCert.certHash)).:+(newCert)
        val newSigner = signer.withAuthenticationCerts(newAuthCerts)
        ctx.api.setVal(signerPrefix + status.creditCode, newSigner)
      }
    } else {
      throw ContractException(toJsonErrMsg(certNotExists))
    }
    null
  }

}
