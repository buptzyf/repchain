package rep.sc.tpl.did.operation

import rep.proto.rc2.{ActionResult, Credential, CredentialContentMetadata}
import rep.sc.scalax.{ContractContext, ContractException}
import rep.sc.tpl.did.DidTplPrefix.{ccmdPrefix, credPrefix, signerPrefix}

/**
  * @author zyf
  */
object CredentialOperation extends DidOperation {

  val ccMetadataExists = ActionResult(16001, "CredentialMetadata已存在")
  val ccMetadataNotExists = ActionResult(16002, "CredentialMetadata不存在")
  val publisherNotTranPoster = ActionResult(16003, "元数据注册者非交易提交者")
  val credentialExists = ActionResult(16004, "Credential已存在")


  object ACTION {
    val signUpCredentialMetadata = "signUpCredentialMetadata"
    val updateCredentialMetadata = "updateCredentialMetadata"
    val publishCredential = "publishCredential"
  }

  /**
    * 注册凭据元数据，只能是自己给自己注册
    *
    * @param ctx
    * @param ccMetadata
    * @return
    */
  def signUpCredentialMetadata(ctx: ContractContext, ccMetadata: CredentialContentMetadata): ActionResult = {
    val ccMetaKey = ccmdPrefix + ccMetadata.id + ccMetadata.metaVersion
    val ccMeta = ctx.api.getVal(ccMetaKey)
    if (ctx.t.getSignature.getCertId.creditCode.equals(ccMetadata.publisher)) {
      if (ccMeta == null) {
        val signer = checkSignerValid(ctx, ccMetadata.publisher)
        val ccMetadataIds = signer.credentialMetadataIds
        val newSigner = signer.withCredentialMetadataIds(ccMetadataIds.:+(ccMetadata.id))
        ctx.api.setVal(signerPrefix + ccMetadata.publisher, newSigner)
        ctx.api.setVal(ccmdPrefix + ccMetaKey, ccMetadata)
      } else {
        throw ContractException(toJsonErrMsg(ccMetadataExists))
      }
    } else {
      throw ContractException(toJsonErrMsg(publisherNotTranPoster))
    }
    null
  }

  /**
    * 更新凭据元数据，只能是自己给自己更新
    *
    * @param ctx
    * @param ccMetadata
    * @return
    */
  def updateCredentialMetadata(ctx: ContractContext, ccMetadata: CredentialContentMetadata): ActionResult = {
    val ccMetaKey = ccmdPrefix + ccMetadata.id + ccMetadata.metaVersion
    val ccMeta = ctx.api.getVal(ccMetaKey)
    if (ctx.t.getSignature.getCertId.creditCode.equals(ccMetadata.publisher)) {
      if (ccMeta != null) {
        checkSignerValid(ctx, ccMetadata.publisher)
        // 直接覆盖
        ctx.api.setVal(ccMetaKey, ccMetadata)
      } else {
        throw ContractException(toJsonErrMsg(ccMetadataNotExists))
      }
    } else {
      throw ContractException(toJsonErrMsg(publisherNotTranPoster))
    }
    null
  }

  /**
    * 凭据发放凭证，发放给别人，保存下来即可
    * 授予人必须是交易提交人
    *
    * @param ctx
    * @param credential
    * @return
    */
  def publishCredential(ctx: ContractContext, credential: Credential): ActionResult = {
    if (ctx.t.getSignature.getCertId.creditCode.equals(credential.getGranter.creditCode)) {
      if (ctx.api.getVal(credPrefix + credential.id) == null) {
        ctx.api.setVal(credPrefix + credential.id, credential)
      } else {
        throw ContractException(toJsonErrMsg(credentialExists))
      }
    } else {
      throw ContractException(toJsonErrMsg(publisherNotTranPoster))
    }
    null
  }

}
