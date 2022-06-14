package rep.sc.tpl.did.operation

import rep.crypto.Sha256
import rep.proto.rc2.{ActionResult, Signer}
import rep.sc.scalax.{ContractContext, ContractException}
import rep.sc.tpl.did.DidTplPrefix.{certPrefix, hashPrefix, signerPrefix}
import rep.utils.IdTool

/**
  * 注册signer，禁用启用signer
  *
  * @author zyf
  */
object SignerOperation extends DidOperation {

  val creditCodeEmpty = ActionResult(12001, "creditCode为空字符串")
  val signerExists = ActionResult(12002, "Signer账户实体已存在")
  val signerNotExists = ActionResult(12003, "Signer账户实体不存在")
  val someFieldsNonEmpty = ActionResult(12004, "certNames/authorizeIds/operateIds/credentialMetadataIds存在非空")
  val authCertExistsCode = 12005
  val authCertExists = "Signer中的身份证书%s已存在"
  val authCertNotExists = ActionResult(12006, "Signer未包含任何身份校验证书")
  val emptyAuthCertHashExists = ActionResult(12007, "存在Hash为空的身份校验证书")
  val customCertExists = ActionResult(12008, "存在普通用户证书或undefinedType证书")
  val SignerCertificateNotMatch = ActionResult(12009, "Signer的creditCode与Certificate中的creditCode不一致")
  val hashNotMatch = ActionResult(12010, "Certificate中hash字段与certificate字段计算得到的Hash不相等")
  val notAdmin = ActionResult(12011, "非super_admin不能修改super_admin的signer的信息或状态")

  case class SignerStatus(creditCode: String, state: Boolean)


  /**
    * 注册Signer
    * 不公开，权限比较高，一般不授予出去
    *
    * @param ctx
    * @param signer
    * @return
    */
  def signUpSigner(ctx: ContractContext, signer: Signer): ActionResult = {
    // 判断signer是否已经存在
    if (ctx.api.getVal(signerPrefix + signer.creditCode) != null) {
      throw ContractException(toJsonErrMsg(signerExists))
    } else if (signer.creditCode.isBlank) {
      // 校验creditCode是否为空，不能为空
      throw ContractException(toJsonErrMsg(creditCodeEmpty))
    } else if (signer.certNames.nonEmpty || signer.authorizeIds.nonEmpty || signer.operateIds.nonEmpty || signer.credentialMetadataIds.nonEmpty) {
      // 校验部分字段是否为非空，必须为空
      throw ContractException(toJsonErrMsg(someFieldsNonEmpty))
    } else if (signer.authenticationCerts.isEmpty) {
      // 判断身份校验证书是否为空，不能为空
      throw ContractException(toJsonErrMsg(authCertNotExists))
    } else if (signer.authenticationCerts.exists(cert => cert.certHash.isBlank)) {
      // 存在Hash为空的身份校验证书，不能为空
      throw ContractException(toJsonErrMsg(emptyAuthCertHashExists))
    } else if (signer.authenticationCerts.exists(cert => cert.certType.isCertCustom || cert.certType.isCertUndefined)) {
      // 判断身份校验证书列表是否存在非身份校验证书
      throw ContractException(toJsonErrMsg(customCertExists))
    } else {
      var certNames = Seq.empty[String]
      // 保存所有的身份校验证书，身份校验证书也可签名用
      signer.authenticationCerts.foreach(cert => {
        val certId = cert.getId
        val certKey = certPrefix + certId.creditCode + "." + certId.certName
        // 需同时判断certHash与certId的存在性，因为对于身份证书，他俩是同时存在，一一对应的
        if (ctx.api.getVal(hashPrefix + cert.certHash) != null) {
          throw ContractException(toJsonErrMsg(authCertExistsCode, authCertExists.format(cert.certHash)))
        } else if (ctx.api.getVal(certKey) != null) {
          throw ContractException(toJsonErrMsg(authCertExistsCode, authCertExists.format(certKey)))
        } else if (!signer.creditCode.equals(certId.creditCode)) {
          throw ContractException(toJsonErrMsg(SignerCertificateNotMatch))
        } else if (!ctx.api.getSha256Tool.hashstr(ctx.api.getChainNetId+IdTool.DIDPrefixSeparator+IdTool.deleteLine(cert.certificate)).equals(cert.certHash)) {
          throw ContractException(toJsonErrMsg(hashNotMatch))
        } else {
          // 身份校验用
          ctx.api.setVal(hashPrefix + cert.certHash, certId.creditCode + "." + certId.certName)
          // 验签用（身份密钥对也可以签名的）
          ctx.api.setVal(certKey, cert)
          // 用来更新signer的certNames列表，存放的为"${did}.${certName}"
          certNames = certNames.:+(certId.creditCode + "." + certId.certName)
        }
      })
      // 保存did账户实体，certNames列表已经更新了
      val newSinger = signer.withCertNames(certNames)
      ctx.api.setVal(signerPrefix + signer.creditCode, newSinger)
    }
    null
  }

  /**
    * 更新Signer
    * 不公开，权限比较高，一般不授予出去
    *
    * @param ctx
    * @param signer
    * @return
    */
  def updateSigner(ctx: ContractContext, signer: Signer): ActionResult = {
    // 非管理员不能修改管理员的账户，即普通用户不能修改super_admin
    if (ctx.api.isAdminCert(signer.creditCode) && ctx.t.getSignature.getCertId.creditCode != signer.creditCode) {
      throw ContractException(toJsonErrMsg(notAdmin))
    }
    val oldSigner = ctx.api.getVal(signerPrefix + signer.creditCode)
    // 判断是否有值
    if (oldSigner != null) {
      val signer_tmp = oldSigner.asInstanceOf[Signer]
      val newSigner = signer_tmp.withMobile(signer.mobile).withSignerInfo(signer.signerInfo)
      ctx.api.setVal(signerPrefix + signer.creditCode, newSigner)
    } else {
      throw ContractException(toJsonErrMsg(signerNotExists))
    }
    null
  }

  /**
    * 禁用或启用Signer
    * 不公开，权限比较高，一般不授予出去
    *
    * @param ctx
    * @param status
    * @return
    */
  def updateSignerStatus(ctx: ContractContext, status: SignerStatus): ActionResult = {
    // 非管理员不能修改管理员的账户，即普通用户不能禁用super_admin
    if (ctx.api.isAdminCert(status.creditCode) && ctx.t.getSignature.getCertId.creditCode != status.creditCode) {
      throw ContractException(toJsonErrMsg(notAdmin))
    }
    val oldSigner = ctx.api.getVal(signerPrefix + status.creditCode)
    // 判断是否有值
    if (oldSigner != null) {
      val signer = oldSigner.asInstanceOf[Signer]
      var newSigner = Signer.defaultInstance
      if (status.state) {
        newSigner = signer.withSignerValid(status.state).clearDisableTime
      } else {
        val disableTime = ctx.t.getSignature.getTmLocal
        newSigner = signer.withSignerValid(status.state).withDisableTime(disableTime)
      }
      ctx.api.setVal(signerPrefix + status.creditCode, newSigner)
    } else {
      throw ContractException(toJsonErrMsg(signerNotExists))
    }
    null
  }

}
