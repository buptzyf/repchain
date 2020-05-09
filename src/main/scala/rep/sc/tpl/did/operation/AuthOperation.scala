package rep.sc.tpl.did.operation

import rep.protos.peer._
import rep.sc.scalax.{ContractContext, ContractException}

/**
  * @author zyf
  */
object AuthOperation extends DidOperation {

  val someOperateNotExistsOrNotValid = ActionResult(15001, "部分操作不存在或者无效")
  val authorizeExistsCode = 15002
  val authorizeExists = "grantedId为%s,已包含为%s的authId"
  val authorizeNotExistsCode = 15003
  val authorizeNotExists = "authId为%s的Authorize不存在"
  val authorizeNotValidCode = 15004
  val authorizeNotValid = "authId为%s的Authorize无效"
  val signerNotGranter = ActionResult(15005, "签名交易提交者非权限的授权者")
  val grantedNotTranPoster = ActionResult(15006, "不能绑定Authorize到非签名交易提交者的证书上")

  case class AuthorizeStatus(authId: String, state: Boolean)

  /**
    * 授予操作
    *
    * @param ctx
    * @param authorize
    * @return
    */
  def grantOperate(ctx: ContractContext, authorize: Authorize): ActionResult = {
    // 检查授权账户的有效性
    val grantSigner = checkSignerValid(ctx, authorize.grant)
    val operateIds = grantSigner.operateIds
    val authorizeIds = grantSigner.authorizeIds
    // 保证交易的提交者才能授权自己拥有的操作
    if (ctx.t.getSignature.getCertId.creditCode.equals(authorize.grant)) {
      // 检查granter是否具有该操作，并且该操作有效
      val checkOpIdValid = (opId: String) => {
        val operateIdsCheck: Boolean = operateIds.contains(opId)
        val authorizeIdsCheck: Boolean = authorizeIds.exists(authorizeId => {
          val authorize = ctx.api.getVal(authorizeId).asInstanceOf[Authorize]
          // 拥有，同时可被无限让渡
          authorize.opId.contains(opId) && authorize.isTransfer.isTransferRepeatedly
        })
        // 拥有操作，或者被授权了操作，在满足二者的前提下，操作还需要满足valid==true
        (operateIdsCheck || authorizeIdsCheck) && ctx.api.getVal(opId).asInstanceOf[Operate].opValid
      }
      // granter 拥有 operateId 且 有效
      if (authorize.opId.forall(opId => checkOpIdValid(opId))) {
        authorize.granted.foreach(grantedId => {
          // 检查被授权账户的有效性
          val grantedSigner = checkSignerValid(ctx, grantedId)
          if (!grantedSigner.authorizeIds.contains(authorize.id)) {
            val newAuthIds = grantedSigner.authorizeIds.+:(authorize.id)
            val newGrantedSigner = grantedSigner.withAuthorizeIds(newAuthIds)
            // 更新signer
            ctx.api.setVal(grantedId, newGrantedSigner)
            // 保存授权权限
            ctx.api.setVal(authorize.id, authorize)
          } else {
            throw ContractException(toJsonErrMsg(authorizeExistsCode, authorizeExists.format(grantedId, authorize.id)))
          }
        })
        null
      } else {
        throw ContractException(toJsonErrMsg(someOperateNotExistsOrNotValid))
      }
    } else {
      throw ContractException(toJsonErrMsg(signerNotGranter))
    }
  }

  /**
    * 禁用授权操作
    *
    * @param ctx
    * @param status
    * @return
    */
  def disableGrantOperate(ctx: ContractContext, status: AuthorizeStatus): ActionResult = {
    val oldAuthorize = ctx.api.getVal(status.authId)
    if (status.state) {
      throw ContractException(toJsonErrMsg(stateNotMatchFunction))
    } else {
      if (oldAuthorize != null) {
        val authorize = oldAuthorize.asInstanceOf[Authorize]
        // 检查签名者是否为授权者
        if (ctx.t.getSignature.getCertId.creditCode.equals(authorize.grant)) {
          // 检查账户的有效性
          checkSignerValid(ctx, authorize.grant)
          val disableTime = ctx.t.getSignature.getTmLocal
          val newAuthorize = authorize.withAuthorizeValid(status.state).withDisableTime(disableTime)
          ctx.api.setVal(authorize.id, newAuthorize)
        } else {
          throw ContractException(toJsonErrMsg(signerNotGranter))
        }
      } else {
        throw ContractException(toJsonErrMsg(authorizeNotExistsCode, authorizeNotExists.format(status.authId)))
      }
    }
    null
  }

  /**
    * 启用授权操作
    *
    * @param ctx
    * @param status
    * @return
    */
  def enableGrantOperate(ctx: ContractContext, status: AuthorizeStatus): ActionResult = {
    // TODO
    null
  }

  /**
    * 绑定权限到证书上
    *
    * @param ctx
    * @param bindCertToAuthorize
    * @return
    */
  def bindCertToAuthorize(ctx: ContractContext, bindCertToAuthorize: BindCertToAuthorize): ActionResult = {
    val signer = checkSignerValid(ctx, ctx.t.getSignature.getCertId.creditCode)
    val authId = bindCertToAuthorize.authorizeId
    if (bindCertToAuthorize.getGranted.creditCode.equals(ctx.t.getSignature.getCertId.creditCode)) {
      if (signer.authorizeIds.contains(authId)) {
        val authorize = ctx.api.getVal(authId).asInstanceOf[Authorize]
        // 如果未被禁用，这可以绑定，此处不判断证书有效性，因为有效无效，验签时候会判断
        if (authorize.authorizeValid) {
          ctx.api.setVal(authId + "_" + bindCertToAuthorize.getGranted.creditCode, bindCertToAuthorize)
        } else {
          throw ContractException(toJsonErrMsg(authorizeNotValidCode, authorizeNotValid.format(authId)))
        }
      } else {
        throw ContractException(toJsonErrMsg(authorizeNotExistsCode, authorizeNotExists.format(authId)))
      }
    } else {
      throw ContractException(toJsonErrMsg(grantedNotTranPoster))
    }
    null
  }

}
