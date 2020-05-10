package rep.sc.tpl.did

import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse
import rep.protos.peer._
import rep.sc.scalax.{ContractContext, IContract}
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
      val disableSigner = "disableSigner"
      val enableSigner = "enableSigner"
    }

    object Certificate {
      val signUpCertificate = "signUpCert"
      val disableCertificate = "disableCert"
      val enableCertificate = "enableCert"

    }

    object Authorize {
      val grantOperate = "grantOperate"
      val disableGrantOperate = "disableGrantOperate"
      val enableGrantOperate = "enableGrantOperate"
      val bindCertToAuthorize = "bindCertToAuthorize"
    }

    object Operate {
      val signUpOperate = "signUpOperate"
      val disableOperate = "disableOperate"
      val enableOperate = "enableOperate"
    }

  }


  implicit val formats = DefaultFormats

  def init(ctx: ContractContext): Unit = {
    println(s"tid: $ctx.t.id")
  }

  def onAction(ctx: ContractContext, action: String, sdata: String): ActionResult = {

    val param = parse(sdata)
    val parser = JsonFormat.parser

    action match {
      case ACTION.Signer.signUpSigner =>
        SignerOperation.signUpSigner(ctx, parser.fromJsonString(sdata)(Signer))

      case ACTION.Signer.disableSigner =>
        SignerOperation.disableSigner(ctx, param.extract[SignerStatus])

      case ACTION.Certificate.signUpCertificate =>
        CertOperation.signUpCertificate(ctx, parser.fromJsonString(sdata)(Certificate))

      case ACTION.Certificate.disableCertificate =>
        CertOperation.disableCertificate(ctx, param.extract[CertStatus])

      case ACTION.Operate.signUpOperate =>
        OperOperation.signUpOperate(ctx, parser.fromJsonString(sdata)(Operate))

      case ACTION.Operate.disableOperate =>
        OperOperation.disableOperate(ctx, param.extract[OperateStatus])

      case ACTION.Authorize.grantOperate =>
        AuthOperation.grantOperate(ctx, parser.fromJsonString(sdata)(Authorize))

      case ACTION.Authorize.bindCertToAuthorize =>
        AuthOperation.bindCertToAuthorize(ctx, parser.fromJsonString(sdata)(BindCertToAuthorize))

      case ACTION.Authorize.disableGrantOperate =>
        AuthOperation.disableGrantOperate(ctx, param.extract[AuthorizeStatus])

      case _ =>
        null
    }
  }
}
