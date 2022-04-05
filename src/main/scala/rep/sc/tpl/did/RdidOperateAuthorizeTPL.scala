package rep.sc.tpl.did

import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse
import rep.proto.rc2._
import rep.sc.scalax.{ContractContext, ContractException, IContract}
import rep.sc.tpl.did.operation.AuthOperation.AuthorizeStatus
import rep.sc.tpl.did.operation.CertOperation.CertStatus
import rep.sc.tpl.did.operation.OperOperation.OperateStatus
import rep.sc.tpl.did.operation.SignerOperation.SignerStatus
import rep.sc.tpl.did.operation.{AuthOperation, CertOperation, OperOperation, SignerOperation}
import scalapb.json4s.JsonFormat


/**
  * @author zyf
  */
object RdidOperateAuthorizeTPL {


}

/**
  * @author zyf
  */
class RdidOperateAuthorizeTPL extends IContract {

  object ACTION {

    object Signer {
      val signUpSigner = "signUpSigner"
      val updateSigner = "updateSigner"
      val updateSignerStatus = "updateSignerStatus"
    }

    object Certificate {
      val signUpCertificate = "signUpCertificate" // 无需授权
      val updateCertificateStatus = "updateCertificateStatus" // 无需授权
      val signUpAllTypeCertificate = "signUpAllTypeCertificate" // 需授权
      val updateAllTypeCertificateStatus = "updateAllTypeCertificateStatus" // 需授权
    }

    object Authorize {
      val grantOperate = "grantOperate"
      val updateGrantOperateStatus = "updateGrantOperateStatus"
      val bindCertToAuthorize = "bindCertToAuthorize"
    }

    object Operate {
      val signUpOperate = "signUpOperate"
      val updateOperateStatus = "updateOperateStatus"
    }

  }


  implicit val formats = DefaultFormats

  def init(ctx: ContractContext): Unit = {
    println(s"tid: $ctx.t.id")
  }

  def onAction(ctx: ContractContext, action: String, sdata: String): ActionResult = {

    // 两种序列化方式，如果复杂的 pb 结构体（比如带有枚举类型），则只能使用 pb 自带的 json 序列化方式
    val param = parse(sdata)
    val parser = JsonFormat.parser
    type AuthorizeJString = String

    action match {
      case ACTION.Signer.signUpSigner =>
        SignerOperation.signUpSigner(ctx, parser.fromJsonString(sdata)(Signer))

      case ACTION.Signer.updateSignerStatus =>
        SignerOperation.updateSignerStatus(ctx, param.extract[SignerStatus])

      case ACTION.Certificate.signUpCertificate =>
        CertOperation.signUpCertificate(ctx, parser.fromJsonString(sdata)(Certificate))

      case ACTION.Certificate.updateCertificateStatus =>
        CertOperation.updateCertificateStatus(ctx, param.extract[CertStatus])

      case ACTION.Certificate.signUpAllTypeCertificate =>
        CertOperation.signUpAllTypeCertificate(ctx, parser.fromJsonString(sdata)(Certificate))

      case ACTION.Certificate.updateAllTypeCertificateStatus =>
        CertOperation.updateAllTypeCertificateStatus(ctx, param.extract[CertStatus])

      case ACTION.Operate.signUpOperate =>
        OperOperation.signUpOperate(ctx, parser.fromJsonString(sdata)(Operate))

      case ACTION.Operate.updateOperateStatus =>
        OperOperation.updateOperateStatus(ctx, param.extract[OperateStatus])

      case ACTION.Authorize.grantOperate =>
        AuthOperation.grantOperate(ctx, param.extract[List[AuthorizeJString]])

      case ACTION.Authorize.bindCertToAuthorize =>
        AuthOperation.bindCertToAuthorize(ctx, parser.fromJsonString(sdata)(BindCertToAuthorize))

      case ACTION.Authorize.updateGrantOperateStatus =>
        AuthOperation.updateGrantOperateStatus(ctx, param.extract[AuthorizeStatus])

      case _ =>
        throw ContractException(JsonFormat.toJsonString(ActionResult(100000, "没有对应的方法")))
    }
  }
}
