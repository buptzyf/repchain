package rep.sc.tpl.did.operation

import rep.protos.peer.Signer
import rep.sc.scalax.{ContractContext, ContractException}
import rep.sc.tpl.did.operation.SignerOperation.signerNotExists


/**
  * @author zyf
  */
protected trait DidOperation {

  val signerNotValid = "Signer状态是无效的"
  val notAuthCert = "非身份校验证书"
  val notChainCert = "非链证书"


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
        throw ContractException(signerNotValid)
      }
    } else {
      throw ContractException(signerNotExists)
    }
  }
}

