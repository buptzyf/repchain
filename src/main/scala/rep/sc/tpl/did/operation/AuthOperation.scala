package rep.sc.tpl.did.operation

import rep.proto.rc2.{ActionResult, Authorize, BindCertToAuthorize, Certificate, Operate}
import rep.sc.scalax.{ContractContext, ContractException}
import rep.sc.tpl.did.DidTplPrefix._
import rep.utils.IdTool
import scalapb.json4s.JsonFormat

/**
  * @author zyf
  */
object AuthOperation extends DidOperation {

  val someOperateNotExistsOrNotValid = ActionResult(15001, "部分操作不存在或者无效")
  val authorizeExistsCode = 15002
  val authorizeExists = "authId为%s的Authorize已经存在"
  val authorizeNotExistsCode = 15003
  val authorizeNotExists = "authId为%s的Authorize不存在"
  val authorizeNotValidCode = 15004
  val authorizeNotValid = "authId为%s的Authorize无效"
  val signerNotGranter = ActionResult(15005, "签名交易提交者非权限的授权者")
  val grantedNotTranPoster = ActionResult(15006, "不能绑定Authorize到非签名交易提交者的证书上")
  val bindCertNotExists = ActionResult(15007, "要绑定的证书不存在")
  val bindCertNotValid = ActionResult(15008, "要绑定的证书无效")
  val grantedIdOrOpIdNotOnlyCode = 15009
  val grantedIdOrOpIdNotOnly = "authId为%s的Authorize的被授权人或操作列表元素不是一个"
  val operHasBeenAuthCode = 15010
  val operHasBeenAuth = "operId为%s的Operate已经被授权过"

  case class AuthorizeStatus(authId: String, state: Boolean)

  /**
    * 公开，授予操作
    * 操作拥有者可以将自己<拥有的操作以及自己被授予的可继续让渡的操作>授予别人
    *
    * @param ctx
    * @param authorizeList 授权操作列表
    * @return
    */
  def grantOperate(ctx: ContractContext, authorizeList: List[String]): ActionResult = {
    authorizeList.foreach(authStr => {
      val authorize = JsonFormat.parser.fromJsonString(authStr)(Authorize)
      // 检查授权账户的有效性
      // val grantSigner = checkSignerValid(ctx, authorize.grant)
      val grantOperSeqIndex = operIdxPrefix + authorize.grant + operIdxSuffix
      val grantOperSeqOld = ctx.api.getVal(grantOperSeqIndex).asInstanceOf[Seq[String]]
      val grantOperSeq = if (grantOperSeqOld == null) Seq.empty else grantOperSeqOld
      val grantAuthSeqIndex = authIdxPrefix + authorize.grant + authIdxSuffix
      val grantAuthSeqOld = ctx.api.getVal(grantAuthSeqIndex).asInstanceOf[Seq[String]]
      val grantAuthSeq = if (grantAuthSeqOld == null) Seq.empty else grantAuthSeqOld
      // 保证交易的提交者才能授权自己拥有的操作
      if (ctx.t.getSignature.getCertId.creditCode.equals(authorize.grant)) {
        if (authorize.opId.length != 1 || authorize.granted.length != 1) {
          throw ContractException(toJsonErrMsg(grantedIdOrOpIdNotOnlyCode, grantedIdOrOpIdNotOnly.format(authorize.id)))
        }
        // 检查granter是否具有该操作，并且该操作有效
        val checkOpIdValid = (opId: String) => {
          val operateIdsCheck: Boolean = grantOperSeq.contains(opId)
          val authorizeIdsCheck: Boolean = grantAuthSeq.exists(authorizeId => {
            val authorize = ctx.api.getVal(authPrefix + authorizeId).asInstanceOf[Authorize]
            // 拥有，有效，同时可被无限让渡
            authorize.opId.contains(opId) && authorize.authorizeValid && authorize.isTransfer.isTransferRepeatedly
          })
          // 拥有操作，或者被授权了操作，在满足二者的前提下，操作还需要满足valid==true
          (operateIdsCheck || authorizeIdsCheck) && ctx.api.getVal(operPrefix + opId).asInstanceOf[Operate].opValid
        }
        if (ctx.api.getVal(authPrefix + authorize.id) == null) {
          // granter 拥有 operateId 且 有效
          if (checkOpIdValid(authorize.opId.head)) {
            // 被授权的账户(因为唯一，只取head即可)
            val grantedId = authorize.granted.head
            // 检查被授权账户的有效性
            // val grantedSigner = checkSignerValid(ctx, grantedId)
            val grantedAuthSeqIndex = authIdxPrefix + grantedId + authIdxSuffix
            val grantedAuthSeqOld = ctx.api.getVal(grantedAuthSeqIndex).asInstanceOf[Seq[String]]
            val grantedAuthSeq = if (grantedAuthSeqOld == null) Seq.empty else grantedAuthSeqOld
            val repeated = grantedAuthSeq.find(authId => {
              val oldAuth = ctx.api.getVal(authPrefix + authId).asInstanceOf[Authorize]
              oldAuth.opId.head.equals(authorize.opId.head)
            })
            if (repeated.isEmpty) {
              val grantedAuthSeqNew = grantedAuthSeq :+ (authorize.id)
              // 更新并保存GrantedAuthSeq
              ctx.api.setVal(grantedAuthSeqIndex, grantedAuthSeqNew)
              // 保存授权权限
              ctx.api.setVal(authPrefix + authorize.id, authorize)
            } else {
              throw ContractException(toJsonErrMsg(operHasBeenAuthCode, operHasBeenAuth.format(repeated.head)))
            }
          } else {
            throw ContractException(toJsonErrMsg(someOperateNotExistsOrNotValid))
          }
        } else {
          throw ContractException(toJsonErrMsg(authorizeExistsCode, authorizeExists.format(authorize.id)))
        }
      } else {
        throw ContractException(toJsonErrMsg(signerNotGranter))
      }
    })
    null
  }

  /**
    * 公开，禁用或启用授权操作
    * 授权者或superAdmin可修改授权状态
    *
    * @param ctx
    * @param status
    * @return
    */
  def updateGrantOperateStatus(ctx: ContractContext, status: AuthorizeStatus): ActionResult = {
    val oldAuthorize = ctx.api.getVal(authPrefix + status.authId)
    if (oldAuthorize != null) {
      val authorize = oldAuthorize.asInstanceOf[Authorize]
      val isAdmin = ctx.api.isAdminCert(ctx.t.getSignature.getCertId.creditCode)
      // 检查签名者是否为授权者
      if (ctx.t.getSignature.getCertId.creditCode.equals(authorize.grant) || isAdmin) {
        // 检查账户的有效性
        // checkSignerValid(ctx, authorize.grant)
        var newAuthorize = Authorize.defaultInstance
        if (status.state) {
          newAuthorize = authorize.withAuthorizeValid(status.state).clearDisableTime
        } else {
          val disableTime = ctx.t.getSignature.getTmLocal
          newAuthorize = authorize.withAuthorizeValid(status.state).withDisableTime(disableTime)
        }
        ctx.api.setVal(authPrefix + authorize.id, newAuthorize)
      } else {
        throw ContractException(toJsonErrMsg(signerNotGranter))
      }
    } else {
      throw ContractException(toJsonErrMsg(authorizeNotExistsCode, authorizeNotExists.format(status.authId)))
    }
    null
  }

  /**
    * 公开，绑定权限到证书上
    *
    * @param ctx
    * @param bindCertToAuthorize
    * @return
    */
  def bindCertToAuthorize(ctx: ContractContext, bindCertToAuthorize: BindCertToAuthorize): ActionResult = {
    if (bindCertToAuthorize.getGranted.creditCode.equals(ctx.t.getSignature.getCertId.creditCode)) {
      val authSeqIndex = authIdxPrefix + bindCertToAuthorize.getGranted.creditCode + authIdxSuffix
      val authSeqOld = ctx.api.getVal(authSeqIndex).asInstanceOf[Seq[String]]
      val authSeq = if (authSeqOld == null) Seq.empty else authSeqOld
      // val signer = checkSignerValid(ctx, bindCertToAuthorize.getGranted.creditCode)
      val authId = bindCertToAuthorize.authorizeId
      if (authSeq.contains(authId)) {
        val authorize = ctx.api.getVal(authPrefix + authId).asInstanceOf[Authorize]
        // 如果未被禁用，这可以绑定，此处不判断证书有效性，因为有效无效，验签时候会判断
        if (authorize.authorizeValid) {
          val certKey = certPrefix + IdTool.getSignerFromCertId(bindCertToAuthorize.getGranted)
          val cert = ctx.api.getVal(certKey)
          if (cert == null) {
            throw ContractException(toJsonErrMsg(bindCertNotExists))
          } else if (cert.asInstanceOf[Certificate].certValid) {
            ctx.api.setVal(bindPrefix + authId + "-" + IdTool.getSignerFromCertId(bindCertToAuthorize.getGranted), true)
          } else {
            throw ContractException(toJsonErrMsg(bindCertNotValid))
          }
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
