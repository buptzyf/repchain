package rep.sc.tpl.did.operation

import rep.authority.check.PermissionVerify
import rep.crypto.Sha256
import rep.proto.rc2.Operate.OperateType
import rep.proto.rc2.{ActionResult, Operate}
import rep.sc.Sandbox.SandboxException
import rep.sc.scalax.{ContractContext, ContractException}
import rep.sc.tpl.did.DidTplPrefix.{operPrefix, signerPrefix}
import rep.utils.IdTool

/**
  * 注册操作，禁用启用操作
  *
  * @author zyf
  */
object OperOperation extends DidOperation {

  val operateExists = ActionResult(14001, "operate已存在")
  val operateNotExists = ActionResult(14002, "operate不存在")
  val canNotDeployContract = ActionResult(14003, "不具有该合约部署部署权限者，不能注册或禁用相应操作")
  val registerNotTranPoster = ActionResult(14004, "register(操作注册者)非交易提交者")
  val onlyAdminCanManageServiceOperate = ActionResult(14005, "非管理员，不具有管理Service的权限")
  val onlyAdminCanRegisterOperate = ActionResult(14005, "非管理员，不能注册管理员相关的操作，如：setState与deploy")
  val operateTypeUndefined = ActionResult(14006, "操作类型未定义")
  val hashNotMatch = ActionResult(14007, "Operate中opId字段与计算得到的Hash不相等")

  case class OperateStatus(opId: String, state: Boolean)


  /**
    * 注册Operate
    * 公开，无需授权，链密钥对为自己注册service，为自己注册deploy与setState，以及did合约相关操作，因此可以公开
    * 普通合约拥有者（操作拥有者）给自己注册合约相关的操作
    *
    * @param ctx
    * @param operate
    * @return
    */
  def signUpOperate(ctx: ContractContext, operate: Operate): ActionResult = {
    val isAdmin = ctx.api.isAdminCert(ctx.t.getSignature.getCertId.creditCode)
    // 检查是否为链密钥对，检查是否为合约部署者
    operate.operateType match {
      case OperateType.OPERATE_SERVICE =>
        if (!isAdmin) {
          throw ContractException(toJsonErrMsg(onlyAdminCanManageServiceOperate))
        }
        // TODO 判断hash是否匹配
      case OperateType.OPERATE_CONTRACT =>
        if (operate.authFullName.endsWith(".deploy") || operate.authFullName.endsWith(".setState")) {
          if (!isAdmin) {
            throw ContractException(toJsonErrMsg(onlyAdminCanRegisterOperate))
          }
        } else {
          // 非deploy与setState以及"RdidOperateAuthorizeTPL.function"的，则必须是合约部署者
          val cert = ctx.t.getSignature.certId.get
          //val contractName = operate.authFullName.split("\\.")(0)
          val contractName = operate.authFullName.substring(0,operate.authFullName.lastIndexOf("."))//.split("\\.")(0)
          try {
            ctx.api.permissionCheck(cert.creditCode, cert.certName, contractName + ".deploy")
          } catch {
            case se: SandboxException =>
              try {
                val netid = operate.authFullName.substring(0,operate.authFullName.indexOf(IdTool.DIDPrefixSeparator))
                ctx.api.permissionCheck(cert.creditCode, cert.certName, netid+s"${IdTool.DIDPrefixSeparator}*.deploy")
              } catch {
                case se: SandboxException =>
                  throw ContractException(toJsonErrMsg(canNotDeployContract), se)
              }
          }
        }
        if (ctx.api.getSha256Tool.hashstr(operate.authFullName) != operate.opId) {
          throw ContractException(toJsonErrMsg(hashNotMatch))
        }
      case OperateType.OPERATE_UNDEFINED =>
        throw ContractException(toJsonErrMsg(operateTypeUndefined))
      case _ =>
        throw ContractException(toJsonErrMsg(operateTypeUndefined))
    }
    val certId = ctx.t.getSignature.getCertId
    if (ctx.api.getVal(operPrefix + operate.opId) == null) {
      // 只允许自己给自己注册
      if (operate.register.equals(certId.creditCode)) {
        // 检查账户的有效性
        val signer = checkSignerValid(ctx, operate.register)
        val newSigner = signer.withOperateIds(signer.operateIds.:+(operate.opId))
        // 将operateId注册到Signer里
        ctx.api.setVal(signerPrefix + operate.register, newSigner)
        // 保存operate
        ctx.api.setVal(operPrefix + operate.opId, operate)
      } else {
        throw ContractException(toJsonErrMsg(registerNotTranPoster))
      }
    } else {
      throw ContractException(toJsonErrMsg(operateExists))
    }
    null
  }

  /**
    * 更新操作状态
    * 公开，无需授权，操作注册者禁用或启用Operate，自己 禁用/启用 自己的操作，或者管理员 禁用/启用 其他用户的操作
    *
    * @param ctx
    * @param status
    * @return
    */
  def updateOperateStatus(ctx: ContractContext, status: OperateStatus): ActionResult = {
    val oldOperate = ctx.api.getVal(operPrefix + status.opId)
    if (oldOperate != null) {
      val operate = oldOperate.asInstanceOf[Operate]
      val tranCertId = ctx.t.getSignature.getCertId
      val isAdmin = ctx.api.isAdminCert(tranCertId.creditCode)
      // 自己禁用自己的操作，或者管理员禁用
      if (operate.register.equals(tranCertId.creditCode) || isAdmin) {
        // 检查账户的有效性
        checkSignerValid(ctx, operate.register)
        var newOperate = Operate.defaultInstance
        if (status.state) {
          newOperate = operate.withOpValid(status.state).clearDisableTime
        } else {
          val disableTime = ctx.t.getSignature.getTmLocal
          newOperate = operate.withOpValid(status.state).withDisableTime(disableTime)
        }
        ctx.api.setVal(operPrefix + status.opId, newOperate)
      } else {
        throw ContractException(toJsonErrMsg(registerNotTranPoster))
      }
    } else {
      throw ContractException(toJsonErrMsg(operateNotExists))
    }
    null
  }

}
