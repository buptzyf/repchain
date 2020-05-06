package rep.sc.tpl.did.operation

import rep.protos.peer.{ActionResult, Signer}
import rep.sc.scalax.{ContractContext, ContractException}

/**
  * @author zyf
  */
object SignerOperation extends DidOperation {

  val creditCodeEmpty = "creditCode为空字符串"
  val signerExists = "Signer账户实体已存在"
  val signerNotExists = "Signer账户实体不存在"
  val someFieldsNonEmpty = "certNames/authorizeIds/operateIds/credentialMetadataIds存在非空"
  val authCertExists = "身份证书%s已存在"
  val authCertNotExists = "不存在身份校验证书"
  val emptyAuthCertHashExists = "存在Hash为空的身份校验证书"
  val customCertExists = "存在普通用户证书或undefinedType证书"
  val stateNotMatchFunction = "状态参数与方法名不匹配"

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
      throw ContractException(notChainCert)
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
    // 校验creditCode是否为空
    if (signer.creditCode.isEmpty) {
      throw ContractException(creditCodeEmpty)
    } else {
      val oldSigner = ctx.api.getVal(signer.creditCode)
      // 判断是否有值
      if (oldSigner == null) {
        if (signer.certNames.nonEmpty || signer.authorizeIds.nonEmpty || signer.operateIds.nonEmpty || signer.credentialMetadataIds.nonEmpty) {
          // 校验部分字段是否为非空
          throw ContractException(someFieldsNonEmpty)
        } else if (signer.authenticationCerts.isEmpty) {
          // 判断身份校验证书是否为空
          throw ContractException(authCertNotExists)
        } else if (signer.authenticationCerts.exists(cert => cert.certHash.isBlank)) {
          // 存在Hash为空的身份校验证书
          throw ContractException(emptyAuthCertHashExists)
        } else if (signer.authenticationCerts.exists(cert => cert.certType.isCertCustom || cert.certType.isCertUndefined)) {
          // 判断身份校验证书列表是否存在非身份校验证书
          throw ContractException(customCertExists)
        } else {
          // 保存所有的身份校验证书，身份校验证书也可签名用
          signer.authenticationCerts.foreach(cert => {
            val certId = cert.getId
            val certKey = certId.creditCode + "_" + certId.certName
            if (ctx.api.getVal(certKey) == null) {
              // 身份校验用
              ctx.api.setVal(cert.certHash, certKey)
              // 验签用（身份密钥对也可以签名的）
              ctx.api.setVal(certKey, cert)
            } else {
              throw ContractException(authCertExists.format(certKey))
            }
          })
          // 保存did账户实体
          ctx.api.setVal(signer.creditCode, signer)
        }
      } else {
        throw ContractException(signerExists)
      }
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
      throw ContractException(stateNotMatchFunction)
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
        throw ContractException(signerNotExists)
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
