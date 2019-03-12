package rep.sc.tpl

import rep.sc.contract._
import rep.protos.peer._
import org.json4s.jackson.JsonMethods._
import scala.collection.mutable.Map
import org.json4s.{DefaultFormats, Formats, jackson}

class BaseContract  extends IContract {
    implicit val formats = DefaultFormats   
  
    object ACTION {
      val SignUp = "SignUp"
      
    }
    
    case class CertStatus(credit_code: String, name: String, status: Boolean)
    
    //TODO 用户证书注册
    def signUp(ctx: ContractContext, data:Signer):ActionResult = {
      null
   }
    //TODO 用户证书禁用
    def updateCertStatus(ctx: ContractContext, data:CertStatus):ActionResult = {
      null
   }

    def init(ctx: ContractContext){      
      println(s"tid: $ctx.t.txid")
    }

    
    /**
     * 合约方法入口
     */
    def onAction(ctx: ContractContext,action:String, sdata:String ): ActionResult={
      val json = parse(sdata)
      
      action match {
       case ACTION.SignUp =>
          println(s"SignUp")
          signUp(ctx, json.extract[Signer])
      }
    }
  
}