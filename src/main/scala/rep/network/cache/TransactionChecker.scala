package rep.network.cache

import akka.actor.Props
import rep.log.RepLogger
import rep.log.httplog.AlertInfo
import rep.network.autotransaction.Topic
import rep.network.base.ModuleBase
import rep.network.module.cfrd.CFRDActorType
import rep.utils.GlobalUtils.EventType
import rep.network.consensus.cfrd.MsgOfCFRD.VoteOfBlocker
import rep.proto.rc2.{Event, Transaction}
import rep.storage.chain.block.BlockSearcher

object TransactionChecker{
  def props(name: String): Props = Props(classOf[TransactionChecker], name)
  case class CheckedTransactionResult(result: Boolean, msg: String)
}

class TransactionChecker (moduleName: String) extends ModuleBase(moduleName){

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("TransactionChecker module start"))
    super.preStart()
  }

  val dataaccess = pe.getRepChainContext.getBlockSearch
  /**
   * 检查交易是否符合规则
   * @param t
   * @param dataAccess
   * @return
   */
  def checkTransaction(t: Transaction, dataAccess: BlockSearcher): TransactionChecker.CheckedTransactionResult = {
    var resultMsg = ""
    var result = false

    //if(pe.getRepChainContext.getConfig.hasPreloadOfApi){
      val sig = t.getSignature
      val tOutSig = t.clearSignature
      val cert = sig.getCertId

      try {
        val siginfo = sig.signature.toByteArray()

        if (pe.getRepChainContext.getSignTool.verify(siginfo, tOutSig.toByteArray, cert)) {
          if (pe.getRepChainContext.getTransactionPool.isExistInCache(t.id) || dataAccess.isExistTransactionByTxId(t.id)) {
            resultMsg = s"The transaction(${t.id}) is duplicated with txid"
          } else {
            result = true
          }
        } else {
          RepLogger.sendAlertToDB(pe.getRepChainContext.getHttpLogger(),
            new AlertInfo("API", 5, s"txid=${t.id},msg=签名验证失败."))
          //失败处理
          resultMsg = s"${t.id} 交易签名验证失败"
        }
      } catch {
        case e: RuntimeException =>
          RepLogger.sendAlertToDB(pe.getRepChainContext.getHttpLogger(),
            new AlertInfo("API", 5, s"txid=${t.id},msg=签名验证异常，error=${e.getMessage}."))
          throw e
      }

    TransactionChecker.CheckedTransactionResult(result, resultMsg)
  }

  private def addTransToCache(t: Transaction) = {
    if(!pe.getRepChainContext.getTransactionPool.isExistInCache(t.id)){
      //交易池中不存在的交易才检查
      val checkedTransactionResult = checkTransaction(t, dataaccess)
      //签名验证成功
      val poolIsEmpty = if(pe.getRepChainContext.getTransactionPool.getCachePoolSize <= 0) true  else false
      if((checkedTransactionResult.result) && !pe.getRepChainContext.getTransactionPool.hasOverflowed ){
      //if( SystemProfile.getMaxCacheTransNum == 0 || pe.getTransPoolMgr.getTransLength() < SystemProfile.getMaxCacheTransNum ){
        pe.getRepChainContext.getTransactionPool.addTransactionToCache(t)
        RepLogger.trace(RepLogger.System_Logger,this.getLogMsgPrefix(s"${pe.getSysTag} trans pool recv,txid=${t.id}"))

        if (poolIsEmpty)//加入交易之前交易池为空，发送抽签消息
        pe.getActorRef(CFRDActorType.ActorType.voter) ! VoteOfBlocker
      }else if(!checkedTransactionResult.result){
        RepLogger.error(RepLogger.System_Logger,this.getLogMsgPrefix(s"${pe.getSysTag} check Transaction error,txid=${t.id}"))
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
