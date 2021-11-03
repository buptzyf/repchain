package rep.sc.tpl.did.operation

import rep.protos.peer.{ActionResult, Operate}
import rep.sc.scalax.{ContractContext, ContractException}
import rep.sc.tpl.did.DidTplPrefix.{operPrefix, signerPrefix}
import rep.storage.IdxPrefix.WorldStateKeyPreFix
import rep.utils.SerializeUtils.deserialise

/**
  * 注册操作，禁用启用操作
  *
  * @author zyf
  */
object OperOperation extends DidOperation {

  val operateExists = ActionResult(14001, "operate已存在")
  val operateNotExists = ActionResult(14002, "operate不存在")
  val notContractDeployer = ActionResult(14003, "非合约部署者，不能注册或禁用相应操作，非管理员不能注册管理员相关的操作")
  val registerNotTranPoster = ActionResult(14004, "register(操作注册者)非交易提交者")
  val onlyAdminCanManageServiceOperate = ActionResult(14005, "非管理员不具有管理Service的权限")
  val contractOwnerNotExists = ActionResult(14006, "注册的合约不存在")

  case class OperateStatus(opId: String, state: Boolean)


  /**
    * 判断交易提交者是否为合约部署者
    * 判断是否具有该合约（从Operate中的合约.方法解析其中的合约名）的deploy操作权限
    *
    * @param ctx
    * @param operate
    * @return
    */
  def isContractDeployer(ctx: ContractContext, operate: Operate): Boolean = {
    var res = false
    // 查看合约开发者
    val key_coder = WorldStateKeyPreFix + operate.authFullName.split("\\.")(0)
    val creditCode = deserialise(ctx.api.sr.Get(key_coder)).asInstanceOf[String]
    if (creditCode == null) {
      throw ContractException(toJsonErrMsg(contractOwnerNotExists))
    } else {
      res = creditCode.equals(ctx.t.getSignature.getCertId.creditCode)
      res
    }
  }

  /**
    * 判断交易提交者是否为合约部署者
    *
    * @param ctx
    * @param opId
    * @return
    */
  def isContractDeployer(ctx: ContractContext, opId: String): Boolean = {
    val operate = ctx.api.getVal(operPrefix + opId).asInstanceOf[Operate]
    isContractDeployer(ctx, operate)
  }


  /**
    * 注册Operate
    * 链密钥对为自己注册service，为自己注册deploy与setState，其他用户通过授权
    * 普通合约拥有者（操作拥有者）给自己注册合约相关的操作
    *
    * @param ctx
    * @param operate
    * @return
    */
  def signUpOperate(ctx: ContractContext, operate: Operate): ActionResult = {
    val isAdmin = ctx.api.isAdminCert(ctx.t.getSignature.getCertId.creditCode)
    if (operate.operateType.isOperateService && !isAdmin) {
      throw ContractException(toJsonErrMsg(onlyAdminCanManageServiceOperate))
    }
    // 检查是否为链密钥对，检查是否为合约部署者
    if (operate.operateType.isOperateService || ((operate.authFullName.endsWith(".deploy") || operate.authFullName.endsWith(".setState")) && isAdmin) || isContractDeployer(ctx, operate)) {
      val certId = ctx.t.getSignature.getCertId
      // 只允许自己给自己注册
      if (!operate.register.equals(certId.creditCode)) {
        throw ContractException(toJsonErrMsg(registerNotTranPoster))
      }
      if (ctx.api.getVal(operPrefix + operate.opId) == null) {
        // 检查账户的有效性
        val signer = checkSignerValid(ctx, operate.register)
        val newSigner = signer.withOperateIds(signer.operateIds.:+(operate.opId))
        // 将operateId注册到Signer里
        ctx.api.setVal(signerPrefix + operate.register, newSigner)
        // 保存operate
        ctx.api.setVal(operPrefix + operate.opId, operate)
      } else {
        throw ContractException(toJsonErrMsg(operateExists))
      }
    } else {
      throw ContractException(toJsonErrMsg(notContractDeployer))
    }
    null
  }

  /**
    * 禁用或启用Operate
    *
    * @param ctx
    * @param status
    * @return
    */
  def updateOperateStatus(ctx: ContractContext, status: OperateStatus): ActionResult = {
    val oldOperate = ctx.api.getVal(operPrefix + status.opId)
    if (oldOperate != null) {
      val operate = oldOperate.asInstanceOf[Operate]
      val certId = ctx.t.getSignature.getCertId
      // 只能禁用自己的操作
      if (!operate.register.equals(certId.creditCode)) {
        throw ContractException(toJsonErrMsg(registerNotTranPoster))
      }
      // 检查账户的有效性
      checkSignerValid(ctx, operate.register)
      val disableTime = ctx.t.getSignature.getTmLocal
      val newOperate = operate.withOpValid(status.state).withDisableTime(disableTime)
      ctx.api.setVal(operPrefix + status.opId, newOperate)
    } else {
      throw ContractException(toJsonErrMsg(operateNotExists))
    }
    null
  }

}
