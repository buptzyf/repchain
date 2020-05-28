package rep.api.rest

import java.util.concurrent.Executors

import akka.actor.Props
import akka.util.Timeout
import rep.api.rest.RestActor.PostResult
import rep.log.RepLogger
import rep.network.base.ModuleBase
import rep.protos.peer.Transaction
import scala.concurrent._
import akka.pattern.ask
import rep.crypto.cert.SignTool
import rep.sc.Sandbox.DoTransactionResult
import rep.sc.SandboxDispatcher.DoTransaction
import rep.sc.TypeOfSender
import rep.utils.GlobalUtils.ActorType

import scala.concurrent.{Await, ExecutionContext, Future}

object VerifySignActor{
  def props(name: String): Props = Props(classOf[VerifySignActor], name)
}

class VerifySignActor(moduleName: String) extends ModuleBase(moduleName) {
  import scala.concurrent.duration._
  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(2))

  implicit val timeout = Timeout(1000.seconds)

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.APIAccess_Logger, this.getLogMsgPrefix("VerifySignActor Start"))
  }

  private def AsyncExecuteTransaction(tran:Transaction): Future[Boolean] = Future{
    var tmp:Boolean = false
    val future = pe.getActorRef(ActorType.transactiondispatcher) ? DoTransaction(tran, "api_" + tran.id, TypeOfSender.FromAPI)
    val result = Await.result(future, timeout.duration).asInstanceOf[DoTransactionResult]
    val rv = result
    rv.err match {
      case None =>
        tmp = true
      case Some(err) =>
        tmp = false
    }
    tmp
  }recover { case e: Exception => false }

  private def asyncVerifySign(tran:Transaction,sysName:String): Future[Boolean] = Future {
    var result  = false
    try{
      val sig = tran.signature.get.signature.toByteArray
      val tOutSig = tran.clearSignature
      val certId = tran.signature.get.certId.get
      result = SignTool.verify(sig, tOutSig.toByteArray, certId, sysName)
    }catch{
      case e:Exception => throw e
    }
    result
  } recover { case e: Exception => false }

  private def asyncExecuteAllTask(tran:Transaction):Boolean={
    val vrf = asyncVerifySign(tran,pe.getSysTag)
    val ptf = AsyncExecuteTransaction(tran)
    val result = for {
      v1 <- vrf
      v2 <- ptf
    } yield (v1 && v2 )
    val result1 = Await.result(result, timeout.duration).asInstanceOf[Boolean]
    result1
  }

  override def receive: Receive = {
    case rvs:VerifySignDispatcher.RequestVerifySign =>
      if(asyncExecuteAllTask(rvs.tran)){
        sender ! PostResult(rvs.tran.id, None, None)
      }else{
        sender ! PostResult(rvs.tran.id, None, Option("验证或者执行错误"))
      }
      /*val sig = rvs.tran.signature.get.signature.toByteArray
      val tOutSig = rvs.tran.clearSignature
      val certId = rvs.tran.signature.get.certId.get
      val sr = SignTool.verify(sig, tOutSig.toByteArray, certId, pe.getSysTag)
      if(sr){
        //println(s"txid=${rvs.tran.id},r=${sr}")
        val future = pe.getActorRef(ActorType.transactiondispatcher) ? DoTransaction(rvs.tran, "api_" + rvs.tran.id, TypeOfSender.FromAPI)
        val result = Await.result(future, timeout.duration).asInstanceOf[DoTransactionResult]
        val rv = result

        rv.err match {
          case None =>
            //预执行正常,提交并广播交易
            pe.getActorRef(ActorType.transactionpool) ! rvs.tran // 给交易池发送消息 ！=》告知（getActorRef）
            if (rv.r == null)
              sender ! PostResult(rvs.tran.id, None, None)
            else
              sender ! PostResult(rvs.tran.id, Some(rv.r), None) // legal_prose need
          case Some(err) =>
            //预执行异常,废弃交易，向api调用者发送异常
            sender ! PostResult(rvs.tran.id, None, Option(err.cause.getMessage))
        }
        rvs.httpresactor ! PostResult(rvs.tran.id, None, None)
        //sender() ! VerifySignDispatcher.ResponseVerifySign(true,"")
      }else{
        //println(s"txid=${rvs.tran.id},r=${sr}")
        rvs.httpresactor ! PostResult(rvs.tran.id, None, Option("验证签名出错"))
        //sender() ! VerifySignDispatcher.ResponseVerifySign(false,"")
      }*/
    case _ => //ignore
  }
}
