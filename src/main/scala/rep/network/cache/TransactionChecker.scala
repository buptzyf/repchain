package rep.network.cache

import akka.actor.Props
import rep.app.conf.SystemProfile
import rep.crypto.cert.SignTool
import rep.log.RepLogger
import rep.network.autotransaction.Topic
import rep.network.base.ModuleBase
import rep.network.module.cfrd.CFRDActorType
import rep.protos.peer.{Event, Transaction}
import rep.storage.ImpDataAccess
import rep.utils.GlobalUtils.EventType
import rep.network.consensus.cfrd.MsgOfCFRD.VoteOfBlocker
import rep.network.util.NodeHelp

object TransactionChecker{
  def props(name: String): Props = Props(classOf[TransactionChecker], name)
  case class CheckedTransactionResult(result: Boolean, msg: String)
}

class TransactionChecker (moduleName: String) extends ModuleBase(moduleName){

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("TransactionChecker module start"))
    super.preStart()
  }

  val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
  /**
   * 检查交易是否符合规则
   * @param t
   * @param dataAccess
   * @return
   */
  def checkTransaction(t: Transaction, dataAccess: ImpDataAccess): TransactionChecker.CheckedTransactionResult = {
    var resultMsg = ""
    var result = false

    if(SystemProfile.getHasPreloadTransOfApi){
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
    }else{
      result = true
    }

    TransactionChecker.CheckedTransactionResult(result, resultMsg)
  }

  private def addTransToCache(t: Transaction) = {
    if(!pe.getTransPoolMgr.findTrans(t.id)){
      //交易池中不存在的交易才检查
      val checkedTransactionResult = checkTransaction(t, dataaccess)
      //签名验证成功
      val poolIsEmpty = pe.getTransPoolMgr.isEmpty
      if((checkedTransactionResult.result) && (SystemProfile.getMaxCacheTransNum == 0 || pe.getTransPoolMgr.getTransLength() < SystemProfile.getMaxCacheTransNum) ){
      //if( SystemProfile.getMaxCacheTransNum == 0 || pe.getTransPoolMgr.getTransLength() < SystemProfile.getMaxCacheTransNum ){
        pe.getTransPoolMgr.putTran(t, pe.getSysTag)
        RepLogger.trace(RepLogger.System_Logger,this.getLogMsgPrefix(s"${pe.getSysTag} trans pool recv,txid=${t.id}"))

        if (poolIsEmpty)//加入交易之前交易池为空，发送抽签消息
        pe.getActorRef(CFRDActorType.ActorType.voter) ! VoteOfBlocker
      }
    }
  }

  override def receive = {
    //处理接收的交易
    case t: Transaction =>
      //保存交易到本地
      sendEvent(EventType.RECEIVE_INFO, mediator, pe.getSysTag, Topic.Transaction, Event.Action.TRANSACTION)
      //if(!NodeHelp.isSameNodeForString(this.selfAddr,NodeHelp.getNodePath(sender()))) {
        addTransToCache(t)
      //}else{
      //  System.err.println(s"recv tx from local,system=${pe.getSysTag}")
      //}
    case _ => //ignore
  }

}
