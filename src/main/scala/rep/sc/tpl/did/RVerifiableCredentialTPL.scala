package rep.sc.tpl.did

import rep.protos.peer.ActionResult
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse
import rep.sc.scalax.{ContractContext, ContractException, IContract}
import rep.sc.tpl.did.RVerifiableCredentialTPL.{SignupCCSParam, SignupVCStatusParam, UpdateCCSStatusParam, UpdateVCClaimStatusParam, UpdateVCStatusParam}
import scalapb.json4s.JsonFormat

/**
 * 可验证凭据Verifiable Credential管理合约，
 * 主要提供凭据属性结构管理及凭据状态管理相关合约方法，
 * 可验证凭据本身由相关使用方链下使用，链上存储可公开信息
 *
 * @author jayTsang created
 */

object RVerifiableCredentialTPL {
  final case class Attr(
                         name: String,
                         `type`: String,
                         required: Boolean,
                         description: String
                       )
  case class SignupCCSParam(
                             id: String,
                             name: String,
                             version: String,
                             description: String,
                             creator: String,
                             created: String,
                             status: String,
                             attributes: Attr
                           )
  type CCS = SignupCCSParam
  final case class UpdateCCSStatusParam(id: String, status: String)

  final case class SignupVCStatusParam(id: String, status: String, revokedClaimIndex: Seq[String])
  type VCStatus = SignupVCStatusParam
  final case class UpdateVCStatusParam(id: String, status: String)
  final case class UpdateVCClaimStatusParam(id: String, revokedClaimIndex: Seq[String])
}

class RVerifiableCredentialTPL extends IContract{

  object Action {
    // actions for verifiable credential claim struct
    val SignupCCS = "SignupCCS"
    val UpdateCCSStatus = "UpdateCCSStatus"

    // actions for verifiable credential/claims status
    val SignupVCStatus = "SignupVCStatus"
    val UpdateVCStatus = "UpdateVCStatus"
    val UpdateVCClaimStatus = "UpdateVCClaimStatus"
  }

  override def init(ctx: ContractContext): Unit = {
    println(s"Inited the contract RVerifiableCredentialTPL by TX with tid: ${ctx.t.id}")
  }

  implicit val formats = DefaultFormats

  override def onAction(ctx: ContractContext, action: String, sdata: String): ActionResult = {
    val param = parse(sdata)

    action match {
      case Action.SignupCCS =>
        SignupCCS(ctx, param.extract[SignupCCSParam])
      case Action.UpdateCCSStatus =>
        UpdateCCSStatus(ctx, param.extract[UpdateCCSStatusParam])
      case Action.SignupVCStatus =>
        SignupVCStatus(ctx, param.extract[SignupVCStatusParam])
      case Action.UpdateVCStatus =>
        UpdateVCStatus(ctx, param.extract[UpdateVCStatusParam])
      case Action.UpdateVCClaimStatus =>
        UpdateVCClaimStatus(ctx, param.extract[UpdateVCClaimStatusParam])
      case _ =>
        throw ContractException(JsonFormat.toJsonString(ActionResult(100000, "没有对应的方法")))
    }
  }

  def SignupCCS(ctx: ContractContext, param: SignupCCSParam): ActionResult = {
    null
  }

  def UpdateCCSStatus(ctx: ContractContext, param: UpdateCCSStatusParam): ActionResult = {
    null
  }

  def SignupVCStatus(ctx: ContractContext, param: SignupVCStatusParam): ActionResult = {
    null
  }

  def UpdateVCStatus(ctx: ContractContext, param: UpdateVCStatusParam): ActionResult = {
    null
  }

  def UpdateVCClaimStatus(ctx: ContractContext, param: UpdateVCClaimStatusParam): ActionResult = {
    null
  }
}
