package rep.sc.tpl.did.operation

import rep.app.conf.SystemProfile
import rep.proto.rc2.{ActionResult, Signer}
import rep.sc.scalax.{ContractContext, ContractException}
import rep.sc.tpl.did.operation.SignerOperation.signerNotExists
import rep.sc.tpl.did.DidTplPrefix.signerPrefix
import scalapb.json4s.JsonFormat


/**
  * @author zyf
  */
protected trait DidOperation {

  val didChainCodeName = SystemProfile.getAccountChaincodeName

  val signerNotValid = ActionResult(11001, "Signer状态是无效的")
  val notChainCert = ActionResult(11002, "非链证书")
  val stateNotMatchFunction = ActionResult(11003, "状态参数与方法名不匹配")

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
    * 检查账户是否有效
    *
    * @param ctx
    * @param creditCode
    * @return
    */
  def checkSignerValid(ctx: ContractContext, creditCode: String): Signer = {
    if (ctx.api.getVal(signerPrefix + creditCode) != null) {
      val signer = ctx.api.getVal(signerPrefix + creditCode).asInstanceOf[Signer]
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

