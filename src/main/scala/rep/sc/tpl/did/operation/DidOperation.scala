package rep.sc.tpl.did.operation

import rep.protos.peer.{ActionResult, Signer}
import rep.sc.scalax.{ContractContext, ContractException}
import rep.sc.tpl.did.operation.SignerOperation.signerNotExists
import scalapb.json4s.JsonFormat


/**
  * @author zyf
  */
protected trait DidOperation {

  val signerNotValid = ActionResult(11001, "Signer状态是无效的")
  val notChainCert = ActionResult(11002, "非链证书")
  val notAuthCert = ActionResult(11003, "非身份校验证书")
  val stateNotMatchFunction = ActionResult(11004, "状态参数与方法名不匹配")

  /**
    * format errMsg
    *
    * @param code   错误码
    * @param reason 错误原因
    * @return
    */
  def toJsonErrMsg(code: Int, reason: String): String = {
    val actionResult = ActionResult(code, reason)
    JsonFormat.toJsonString(actionResult)
  }

  /**
    * format errMsg
    *
    * @param actionResult
    * @return
    */
  def toJsonErrMsg(actionResult: ActionResult): String = {
    JsonFormat.toJsonString(actionResult)
  }

  /**
    * 判断是否为链证书
    *
    * @param ctx
    * @return
    */
  def checkChainCert(ctx: ContractContext): Boolean = {
    // TODO
    val result = true
    if (!result) {
      false
    } else {
      result
    }
  }

  /**
    * 检查是否具有相应的操作权限
    *
    * @param ctx
    * @return
    */
  def checkAuthorize(ctx: ContractContext): Boolean = {
    // TODO
    val checkResult = true
    //    checkResult = ctx.api.verifyOfContract(ctx.t.signature.get.getCertId)
    if (!checkResult) {
      //      throw ContractException("没有相应操作权限")
      false
    } else {
      checkResult
    }
  }

  /**
    * 检查账户是否有效
    *
    * @param ctx
    * @param creditCode
    * @return
    */
  def checkSignerValid(ctx: ContractContext, creditCode: String): Signer = {
    if (ctx.api.getVal(creditCode) != null) {
      val signer = ctx.api.getVal(creditCode).asInstanceOf[Signer]
      if (signer.signerValid) {
        signer
      } else {
        throw ContractException(toJsonErrMsg(signerNotValid))
      }
    } else {
      throw ContractException(toJsonErrMsg(signerNotExists))
    }
  }
}

