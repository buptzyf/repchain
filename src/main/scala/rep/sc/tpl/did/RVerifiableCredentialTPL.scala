package rep.sc.tpl.did

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import rep.protos.peer.{ActionResult, CreClaStruct, CreAttr, VerCreStatus}
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse
import rep.sc.scalax.{ContractContext, ContractException, IContract}
import scalapb.json4s.JsonFormat
import rep.storage.IdxPrefix._
import scala.util.matching.Regex
import rep.utils.GlobalUtils.DID_INITIAL_CHARS
import rep.sc.tpl.did.DidTplPrefix._

/**
 * 可验证凭据Verifiable Credential管理合约，
 * 主要提供凭据属性结构CCS管理及凭据状态VCS管理的相关合约方法，
 * 可验证凭据VC本身由相关使用方在链下传递使用，链上存储其属性结构和状态等可公开信息
 *
 * @author jayTsang created
 */

class RVerifiableCredentialTPL extends IContract{

  import RVerifiableCredentialTPL._



  override def init(ctx: ContractContext): Unit = {
    val regex = new Regex(s"$WorldStateKeyPreFix|_")
    val contractName = regex.replaceAllIn(ctx.api.pre_key, "")
    println(s"Inited the contract $contractName by TX with the tid: ${ctx.t.id}")
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
      case Action.RevokeVCClaims =>
        RevokeVCClaims(ctx, param.extract[RevokeVCClaimsParam])
      case f =>
        throw ContractException(
          JsonFormat.toJsonString(ActionResult(STATUS_CODE_NO_FUNCTION, s"没有对应的合约方法:${f}"))
        )
    }
  }

  /**
   * 注册可验证凭据属性结构CCS
   * @param ctx
   * @param param
   * @return
   */
  def SignupCCS(ctx: ContractContext, param: SignupCCSParam): ActionResult = {
    assertSignupCCSParam(param)

    val ccsStateKey = ccsPrefix + param.id

    assertNewWorldstate(
      ctx.api.getVal(ccsStateKey),
      s"已存在可验证凭据属性结构，CCS(id:${param.id})"
    )

    val creator = DID_INITIAL_CHARS + ctx.t.signature.get.certId.get.creditCode
    val valid = true
    val version = "1.0"
    val ccs = CreClaStruct(
      id = param.id,
      name = param.name,
      ccsVersion = param.version,
      description = param.description,
      creator = creator,
      created = param.created,
      valid = valid,
      attributes = param.attributes.map(
        attr => CreAttr(attr.name, attr.`type`, attr.required, attr.description)
      ),
      version = version
    )

    ctx.api.setVal(ccsStateKey, ccs)

    ActionResult(STATUS_CODE_OK, s"成功调用注册可验证凭据属性结构方法，CCS(id: ${ccs.id})")
  }

  /**
   * 更新可验证凭据属性结构CCS的有效性
   * @param ctx
   * @param param
   * @return
   */
  def UpdateCCSStatus(ctx: ContractContext, param: UpdateCCSStatusParam): ActionResult = {
    assertUpdateCCSStatusParam(param)

    val ccsStateKey = ccsPrefix + param.id
    val ccs = ctx.api.getVal(ccsStateKey).asInstanceOf[CreClaStruct]

    assertExistedWorldstate(
      ccs,
      s"没有对应的可验证凭据属性结构，CCS(id: ${param.id})"
    )
    assertInvokerIsCreator(
      ctx,
      ccs,
      s"更新可验证凭据属性结构有效性方法的调用者不是相应的创建者，CCS(id: ${ccs.id})"
    )

    ctx.api.setVal(ccsStateKey, ccs.withValid(param.valid))

    ActionResult(STATUS_CODE_OK, s"成功调用更新可验证凭据属性结构有效性方法，CCS(id: ${ccs.id})")
  }

  /**
   * 注册可验证凭据状态信息VCS
   * @param ctx
   * @param param
   * @return
   */
  def SignupVCStatus(ctx: ContractContext, param: SignupVCStatusParam): ActionResult = {
    assertSignupVCStatusParam(param)

    val vcsStateKey = vcsPrefix + param.id

    assertNewWorldstate(
      ctx.api.getVal(vcsStateKey),
      s"已存在可验证凭据状态信息，VCS(id:${param.id})"
    )

    val creator = DID_INITIAL_CHARS + ctx.t.signature.get.certId.get.creditCode
    val version = "1.0"
    val vcs = VerCreStatus(
      id = param.id,
      status = param.status,
      revokedClaimIndex = Seq(),
      creator = creator,
      version = version
    )
    ctx.api.setVal(vcsStateKey, vcs)

    ActionResult(STATUS_CODE_OK, s"成功调用注册可验证凭据状态信息方法，VCS(id: ${vcs.id})")
  }

  /**
   * 更新可验证凭据状态V，即更新整个可验证凭据的状态
   * @param ctx
   * @param param
   * @return
   */
  def UpdateVCStatus(ctx: ContractContext, param: UpdateVCStatusParam): ActionResult = {
    assertUpdateVCStatusParam(param)

    val vcsStateKey = vcsPrefix + param.id
    val vcs = ctx.api.getVal(vcsStateKey).asInstanceOf[VerCreStatus]

    assertExistedWorldstate(
      vcs,
      s"没有对应的可验证凭据状态，VCS(id: ${param.id})"
    )
    assertInvokerIsCreator(
      ctx,
      vcs,
      s"更新可验证凭据状态方法的调用者不是相应的创建者，VCS(id: ${vcs.id})"
    )

    ctx.api.setVal(vcsStateKey, vcs.withStatus(param.status))

    ActionResult(STATUS_CODE_OK, s"成功调用更新可验证凭据状态方法，VCS(id: ${vcs.id})")
  }

  /**
   * 撤销可验证凭据属性，即废除可验证凭据中部分凭据属性
   * @param ctx
   * @param param
   * @return
   */
  def RevokeVCClaims(ctx: ContractContext, param: RevokeVCClaimsParam): ActionResult = {
    assertRevokeVCClaimsParam(param)

    val vcsStateKey = vcsPrefix + param.id
    val vcs = ctx.api.getVal(vcsStateKey).asInstanceOf[VerCreStatus]

    assertExistedWorldstate(
      vcs,
      s"没有对应的可验证凭据状态，VCS(id: ${param.id})"
    )
    assertInvokerIsCreator(
      ctx,
      vcs,
      s"撤销可验证凭据属性方法的调用者不是相应的创建者，VCS(id: ${vcs.id})"
    )

    val revokedClaimIndex = vcs.revokedClaimIndex
      .union(param.revokedClaimIndex).distinct
    ctx.api.setVal(vcsStateKey, vcs.withRevokedClaimIndex(revokedClaimIndex))

    ActionResult(STATUS_CODE_OK, s"成功调用撤销可验证凭据属性方法，VCS(id: ${vcs.id})")
  }
}

object RVerifiableCredentialTPL {

  object Action {
    // actions for verifiable credential claim struct
    val SignupCCS = "signupCCS"
    val UpdateCCSStatus = "updateCCSStatus"

    // actions for verifiable credential/claims status
    val SignupVCStatus = "signupVCStatus"
    val UpdateVCStatus = "updateVCStatus"
    val RevokeVCClaims = "revokeVCClaims"
  }

  final case class SignupCCSAttrParam(
                                       name: String,
                                       `type`: String,
                                       required: Boolean = true,
                                       description: String
                                     )
  final case class SignupCCSParam(
                                   id: String,
                                   name: String,
                                   version: String, // ccsVersion
                                   created: String,
                                   description: String,
                                   attributes: Seq[SignupCCSAttrParam]
                                 )
  final case class UpdateCCSStatusParam(id: String, valid: Boolean)

  final case class SignupVCStatusParam(id: String, status: String)
  final case class UpdateVCStatusParam(id: String, status: String)
  final case class RevokeVCClaimsParam(id: String, revokedClaimIndex: Seq[String])

  val STATUS_CODE_OK             = 500200 // 合约方法调用成功
  val STATUS_CODE_NO_FUNCTION    = 500300 // 无对应合约方法
  val STATUS_CODE_BAD_REQUEST    = 500400 // 合约方法参数有误
  val STATUS_CODE_UNAUTHORIZED   = 500401 // 没有该合约方法调用权限
  val STATUS_CODE_NOT_FOUND      = 500404 // 找不到对应合约状态资源worldstate
  val STATUS_CODE_ALREADY_EXISTS = 500410 // 对应合约状态资源worldstate已存在

  val DID_LOCATION_DELIMITER = "#"

  def assertSignupCCSParam(param: SignupCCSParam) = {
    assertRequiredParamStrField("id", param.id)
    assertRequiredParamStrField("name", param.name)
    assertRequiredParamStrField("version", param.version)
    assertRequiredParamStrField("description", param.description)
    assertRequiredParamStrField("created", param.created)
    assertRequiredParamSeqSignupCCSAttrField("attributes", param.attributes)
  }
  def assertUpdateCCSStatusParam(param: UpdateCCSStatusParam) = {
    assertRequiredParamStrField("id", param.id)
  }

  def assertSignupVCStatusParam(param: SignupVCStatusParam) = {
    assertRequiredParamStrField("id", param.id)
    assertRequiredParamStrField("status", param.status)
  }
  def assertUpdateVCStatusParam(param: UpdateVCStatusParam) = {
    assertRequiredParamStrField("id", param.id)
    assertRequiredParamStrField("status", param.status)
  }
  def assertRevokeVCClaimsParam(param: RevokeVCClaimsParam) = {
    assertRequiredParamStrField("id", param.id)
    assertRequiredParamSeqStrField("revokedClaimIndex", param.revokedClaimIndex)
  }

  def assertRequiredParamStrField(fieldName: String, fieldValue: String): Null = {
    if (fieldValue.isBlank) {
      throw ContractException(JsonFormat.toJsonString(
        ActionResult(STATUS_CODE_BAD_REQUEST, s"参数字段${fieldName}不能为空字符串")
      ))
    }
    null
  }
  def assertRequiredParamSeqStrField(fieldName: String, fieldValue: Seq[String]): Null = {
    if (fieldValue.length == 0) {
      throw ContractException(
        JsonFormat.toJsonString(
          ActionResult(STATUS_CODE_BAD_REQUEST, s"参数字段${fieldName}不能为空数组")
        )
      )
    }
    null
  }
  def assertRequiredParamSeqSignupCCSAttrField(fieldName: String, fieldValue: Seq[SignupCCSAttrParam]): Null = {
    if (fieldValue.length == 0) {
      throw ContractException(
        JsonFormat.toJsonString(
          ActionResult(STATUS_CODE_BAD_REQUEST, s"参数字段${fieldName}不能为空数组")
        )
      )
    }
    fieldValue.zipWithIndex.foreach {
      case(attr, i) => {
        assertRequiredParamStrField(s"${fieldName}[${i}].name", attr.name)
        assertRequiredParamStrField(s"${fieldName}[${i}].type", attr.`type`)
        assertRequiredParamStrField(s"${fieldName}[${i}].description", attr.description)
      }
    }
    null
  }

  /**
   * worldstate不应已存在
   * @param worldstate
   * @param message
   */
  def assertNewWorldstate(worldstate: Any, message: String) = {
    if(worldstate != null) {
      throw ContractException(
        JsonFormat.toJsonString(
          ActionResult(STATUS_CODE_ALREADY_EXISTS, message)
        )
      )
    }
  }

  /**
   * worldstate应当已存在
   * @param worldstate
   * @param message
   */
  def assertExistedWorldstate(worldstate: Any, message: String) = {
    if( worldstate == null) {
      throw ContractException(
        JsonFormat.toJsonString(ActionResult(STATUS_CODE_NOT_FOUND, message))
      )
    }
  }

  /**
   * 合约方法调用者应当是worldstate的创建者
   * @param worldstate
   * @param message
   */
  def assertInvokerIsCreator(ctx: ContractContext, worldstate: Any, message: String) = {
    var creator = ""
    worldstate match {
      case ccs: CreClaStruct => creator = ccs.creator.split(":").last
      case vcs: VerCreStatus => creator = vcs.creator.split(":").last
    }
    if(creator != ctx.t.signature.get.certId.get.creditCode) {
      throw ContractException(
        JsonFormat.toJsonString(
          ActionResult(
            STATUS_CODE_UNAUTHORIZED,
            message
          )
        )
      )
    }
  }
}
