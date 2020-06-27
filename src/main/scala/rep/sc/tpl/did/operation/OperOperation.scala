package rep.sc.tpl.did.operation

import rep.protos.peer.{ActionResult, Operate}
import rep.sc.scalax.{ContractContext, ContractException}
import rep.sc.tpl.did.DidTplPrefix.{signerPrefix, operPrefix}

/**
  * @author zyf
  */
object OperOperation extends DidOperation {

  val operateExists = ActionResult(14001, "operate已存在")
  val operateNotExists = ActionResult(14002, "operate不存在")
  val notContractDeployerOrChainAdmin = ActionResult(14003, "非合约部署者或链密钥对，不能注册相应操作")
  val registerNotTranPoster = ActionResult(14004, "register(操作注册者)非交易提交者")

  case class OperateStatus(opId: String, state: Boolean)


  /**
    * 判断交易提交者是否为合约部署者
    *
    * @param ctx
    * @param operate
    * @return
    */
  def operateOwner(ctx: ContractContext, operate: Operate): Boolean = {
    // TODO
    true
  }


  /**
    * 注册Operate
    * 链密钥对为普通合约拥有者注册，普通合约拥有者（操作拥有者）给自己注册
    *
    * @param ctx
    * @param operate
    * @return
    */
  def signUpOperate(ctx: ContractContext, operate: Operate): ActionResult = {
    val res1 = checkChainCert(ctx)
    val res2 = operateOwner(ctx, operate)
    // 检查是否为链密钥对，检查是否为合约部署者
    if (res1 || res2) {
      val certId = ctx.t.getSignature.getCertId
      // 非链密钥对，只允许自己给自己注册
      if (!res1 && res2 && !operate.register.equals(certId.creditCode)) {
        throw ContractException(toJsonErrMsg(registerNotTranPoster))
      }
      // 链密钥对为普通合约拥有者注册，普通合约拥有者（操作拥有者）给自己注册
      if (ctx.api.getVal(operPrefix + operate.opId) == null) {
        // 检查账户的有效性
        val signer = checkSignerValid(ctx, operate.register)
        val newSigner = signer.withOperateIds(signer.operateIds.:+(operate.opId))
        // 将operateId注册到Signer里
        ctx.api.setVal(signerPrefix + operate.register, newSigner)
        // 保存operate
        ctx.api.setVal(operPrefix + operate.opId, operate)
        // 如果operate为publish的（无需授权），则加到公共列表里
        //if (operate.isPublish) {
          /*val openList = ctx.api.getVal("all_ops")
          if (openList == null) {
            val newOpenList = List(operate.opId)
            ctx.api.setVal("all_ops", newOpenList)
          } else {
            val newOpenList = openList.asInstanceOf[List[String]].:+(operate.opId)
            ctx.api.setVal("all_ops", newOpenList)
          }*/
        //}
      } else {
        throw ContractException(toJsonErrMsg(operateExists))
      }
    } else {
      throw ContractException(toJsonErrMsg(notContractDeployerOrChainAdmin))
    }
    null
  }

  /**
    * 禁用Operate
    *
    * @param ctx
    * @param status
    * @return
    */
  def disableOperate(ctx: ContractContext, status: OperateStatus): ActionResult = {
    if (status.state) {
      throw ContractException(toJsonErrMsg(stateNotMatchFunction))
    } else {
      val oldOperate = ctx.api.getVal(operPrefix + status.opId)
      if (oldOperate != null) {
        val operate = oldOperate.asInstanceOf[Operate]
        val res1 = checkChainCert(ctx)
        val res2 = operateOwner(ctx, operate)
        // 检查是否为链密钥对，检查是否为合约部署者
        if (res1 || res2) {
          val certId = ctx.t.getSignature.getCertId
          if (!res1 && res2 && !operate.register.equals(certId.creditCode)) {
            throw ContractException(toJsonErrMsg(registerNotTranPoster))
          }
          // 检查账户的有效性
          checkSignerValid(ctx, operate.register)
          val disableTime = ctx.t.getSignature.getTmLocal
          val newOperate = operate.withOpValid(status.state).withDisableTime(disableTime)
          ctx.api.setVal(operPrefix + status.opId, newOperate)
        } else {
          throw ContractException(toJsonErrMsg(notContractDeployerOrChainAdmin))
        }
      } else {
        throw ContractException(toJsonErrMsg(operateNotExists))
      }
    }
    null
  }

  /**
    * 启用Operate
    *
    * @param ctx
    * @param status
    * @return
    */
  def enableOperate(ctx: ContractContext, status: OperateStatus): ActionResult = {
    // TODO
    null
  }
}
