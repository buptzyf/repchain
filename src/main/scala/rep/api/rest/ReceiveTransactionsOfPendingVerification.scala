package rep.api.rest

import akka.actor.{ActorSelection, Props}
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import rep.app.conf.SystemProfile
import rep.crypto.cert.SignTool
import rep.log.RepLogger
import rep.network.autotransaction.Topic
import rep.network.base.ModuleBase
import rep.network.cache.ITransactionPool.CheckedTransactionResult
import rep.protos.peer.Transaction
import rep.storage.ImpDataAccess

object ReceiveTransactionsOfPendingVerification{
  def props(name: String): Props = Props(classOf[ReceiveTransactionsOfPendingVerification],name)
}

class ReceiveTransactionsOfPendingVerification (moduleName: String) extends ModuleBase(moduleName)  {
  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "ReceiveTransactionsOfPendingVerification Start"))
  }

  protected val recvTransactionActorName = "/user/modulemanager/transactionpool"
  val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)

  def checkTransaction(t: Transaction, dataAccess: ImpDataAccess): CheckedTransactionResult = {
    var resultMsg = ""
    var result = false

    val sig = t.getSignature
    val tOutSig = t.clearSignature
    val cert = sig.getCertId

    try {
      val siginfo = sig.signature.toByteArray()

      if (SignTool.verify(siginfo, tOutSig.toByteArray, cert, pe.getSysTag)) {
        if (pe.getTransPoolMgr.findTrans(t.id) || dataAccess.isExistTrans4Txid(t.id)) {
          resultMsg = s"The transaction(${t.id}) is duplicated with txid"
        } else {
          result = true
        }
      } else {
        resultMsg = s"The transaction(${t.id}) is not completed"
      }
    } catch {
      case e: RuntimeException => throw e
    }

    CheckedTransactionResult(result, resultMsg)
  }

  override def receive = {
    //处理接收的交易
    case t: Transaction =>
      //System.out.println(s"outputer : ${pe.getSysTag},entry verify transaction ,from:${t.id}")
      if(this.checkTransaction(t,dataaccess).result){
        pe.getNodeMgr.getStableNodes.foreach(a=>{
          val addr = a.toString + recvTransactionActorName
          val selection: ActorSelection = context.actorSelection(addr)
          try{
            selection ! t
          }catch{
            case e:Exception=> e.printStackTrace()
          }
        })

        //val selection: ActorSelection = context.actorSelection(toAkkaUrl(SystemProfile.getValidatorAddr.get(validatorAddrIdx), validatorActorName));
        //mediator ! Publish(Topic.Transaction, t)
      }

    case _ => //ignore
  }
}
