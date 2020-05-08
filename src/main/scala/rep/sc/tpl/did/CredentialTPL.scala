package rep.sc.tpl.did

import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse
import rep.protos.peer.{ActionResult, Credential, CredentialContentMetadata}
import rep.sc.scalax.{ContractContext, IContract}
import rep.sc.tpl.did.operation.CredentialOperation

/**
  * @author zyf
  */
class CredentialTPL extends IContract {

  implicit val formats = DefaultFormats

  object ACTION {
    val signUpCredentialMetadata = "signUpCredentialMetadata"
    val updateCredentialMetadata = "updateCredentialMetadata"
    val publishCredential = "publishCredential"
  }


  def init(ctx: ContractContext): Unit = {
    println(s"tid: $ctx.t.id")
  }

  def onAction(ctx: ContractContext, action: String, sdata: String): ActionResult = {

    val param = parse(sdata)

    action match {
      case ACTION.signUpCredentialMetadata =>
        CredentialOperation.signUpCredentialMetadata(ctx, param.extract[CredentialContentMetadata])

      case ACTION.updateCredentialMetadata =>
        CredentialOperation.updateCredentialMetadata(ctx, param.extract[CredentialContentMetadata])

      case ACTION.publishCredential =>
        CredentialOperation.publishCredential(ctx, param.extract[Credential])
        
      case _ =>
        null
    }
  }

}
