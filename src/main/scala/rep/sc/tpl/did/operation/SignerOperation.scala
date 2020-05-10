package rep.sc.tpl.did.operation

import rep.protos.peer.{ActionResult, Signer}
import rep.sc.scalax.{ContractContext, ContractException}

/**
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

  case class SignerStatus(creditCode: String, state: Boolean)

  /**
    * 判断是否为链证书
    *
    * @param ctx
    * @return
    */
  override def checkChainCert(ctx: ContractContext): Boolean = {
    // TODO
    val result = true
    if (!result) {
      throw ContractException(toJsonErrMsg(notChainCert))
    } else {
      result
    }
  }

  /**
    * 注册Signer
    *
    * @param ctx
    * @param signer
    * @return
    */
  def signUpSigner(ctx: ContractContext, signer: Signer): ActionResult = {
    // 检查是否为链证书，非链证书，异常抛出
    checkChainCert(ctx)
    // 判断signer是否已经存在
    if (ctx.api.getVal(signer.creditCode) != null) {
      throw ContractException(toJsonErrMsg(signerExists))
    } else if (signer.creditCode.isEmpty) {
      // 校验creditCode是否为空
      throw ContractException(toJsonErrMsg(creditCodeEmpty))
    } else if (signer.certNames.nonEmpty || signer.authorizeIds.nonEmpty || signer.operateIds.nonEmpty || signer.credentialMetadataIds.nonEmpty) {
      // 校验部分字段是否为非空
      throw ContractException(toJsonErrMsg(someFieldsNonEmpty))
    } else if (signer.authenticationCerts.isEmpty) {
      // 判断身份校验证书是否为空
      throw ContractException(toJsonErrMsg(authCertNotExists))
    } else if (signer.authenticationCerts.exists(cert => cert.certHash.isBlank)) {
      // 存在Hash为空的身份校验证书
      throw ContractException(toJsonErrMsg(emptyAuthCertHashExists))
    } else if (signer.authenticationCerts.exists(cert => cert.certType.isCertCustom || cert.certType.isCertUndefined)) {
      // 判断身份校验证书列表是否存在非身份校验证书
      throw ContractException(toJsonErrMsg(customCertExists))
    } else {
      var certNames = Seq.empty[String]
      // 保存所有的身份校验证书，身份校验证书也可签名用
      signer.authenticationCerts.foreach(cert => {
        val certId = cert.getId
        val certKey = certId.creditCode + "_" + certId.certName
        // 需同时判断certHash与certId的存在性，因为对于身份证书，他俩是同时存在，一一对应的
        if (ctx.api.getVal(cert.certHash) != null) {
          throw ContractException(toJsonErrMsg(authCertExistsCode, authCertExists.format(cert.certHash)))
        } else if (ctx.api.getVal(certKey) != null) {
          throw ContractException(toJsonErrMsg(authCertExistsCode, authCertExists.format(certKey)))
        } else if (!signer.creditCode.equals(certId.creditCode)) {
          throw ContractException(toJsonErrMsg(SignerCertificateNotMatch))
        }else {
          // 身份校验用
          ctx.api.setVal(cert.certHash, certKey)
          // 验签用（身份密钥对也可以签名的）
          ctx.api.setVal(certKey, cert)
          // 用来更新signer的certNames列表，存放的为"did_certName"
          certNames = certNames.:+(certKey)
        }
      })
      // 保存did账户实体，certNames列表已经更新了
      val newSinger = signer.withCertNames(certNames)
      ctx.api.setVal(signer.creditCode, newSinger)
    }
    null
  }

  /**
    * 更新Signer
    *
    * @param ctx
    * @param data
    * @return
    */
  def updateSigner(ctx: ContractContext, signer: Signer): ActionResult = {
    // TODO
    null
  }

  /**
    * 禁用Signer
    *
    * @param ctx
    * @param status
    * @return
    */
  def disableSigner(ctx: ContractContext, status: SignerStatus): ActionResult = {
    if (status.state) {
      throw ContractException(toJsonErrMsg(stateNotMatchFunction))
    } else {
      // 检查是否为链证书，非链证书，异常抛出
      checkChainCert(ctx)
      val oldSigner = ctx.api.getVal(status.creditCode)
      // 判断是否有值
      if (oldSigner != null) {
        val signer = oldSigner.asInstanceOf[Signer]
        val disableTime = ctx.t.getSignature.getTmLocal
        val newSigner = signer.withSignerValid(status.state).withDisableTime(disableTime)
        ctx.api.setVal(status.creditCode, newSigner)
      } else {
        throw ContractException(toJsonErrMsg(signerNotExists))
      }
    }
    null
  }

  /**
    * 启用Signer
    *
    * @param ctx
    * @param data
    * @return
    */
  def enableSigner(ctx: ContractContext, status: SignerStatus): ActionResult = {
    // TODO
    null
  }
}
