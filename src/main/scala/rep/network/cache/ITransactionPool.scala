package rep.network.cache

import akka.actor.{Address, Props}
import rep.log.RepLogger
import rep.network.autotransaction.Topic
import rep.network.base.ModuleBase
import rep.network.cache.ITransactionPool.CheckedTransactionResult
import rep.network.util.NodeHelp
import rep.proto.rc2.{Event, Transaction}
import rep.storage.chain.block.BlockSearcher
import rep.utils.GlobalUtils.EventType

/**
 * Created by jiangbuyun on 2020/03/19.
 * 抽象的交易池actor
 */

object ITransactionPool{
  def props(name: String): Props = Props(classOf[ITransactionPool], name)
  //交易检查结果
  case class CheckedTransactionResult(result: Boolean, msg: String)
}

abstract class ITransactionPool (moduleName: String) extends ModuleBase(moduleName) {
  import akka.actor.ActorSelection

  private val transPoolActorName = "/user/modulemanager/transactionpool"
  private var addr4NonUser = ""
  val searcher = pe.getRepChainContext.getBlockSearch
  val config = pe.getRepChainContext.getConfig

  override def preStart(): Unit = {
    //注册接收交易的广播
    if (pe.getRepChainContext.getConsensusNodeConfig.getVoteListOfConfig.contains(pe.getSysTag)) {
      //共识节点可以订阅交易的广播事件
      if (config.useCustomBroadcast) {
        pe.getRepChainContext.getCustomBroadcastHandler.SubscribeTopic(Topic.Transaction, transPoolActorName)
        RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix(s"Subscribe custom broadcast,${transPoolActorName}"))
      }
      //else {
        SubscribeTopic(mediator, self, selfAddr, Topic.Transaction, true)
        RepLogger.info(RepLogger.System_Logger,this.getLogMsgPrefix(s"Subscribe system broadcast,${transPoolActorName}"))
      //}
    }
  }

  def toAkkaUrl(sn: Address, actorName: String): String = {
    return sn.toString + "/" + actorName;
  }

  def visitStoreService(sn: Address, actorName: String, t1: Transaction) = {
    try {
      val selection: ActorSelection = context.actorSelection(toAkkaUrl(sn, actorName));
      selection ! t1
    } catch {
      case e: Exception => e.printStackTrace()
    }
  }

  /**
   * 检查交易是否符合规则
   * @param t
   * @param dataAccess
   * @return
   */
  def checkTransaction(t: Transaction, dataAccess: BlockSearcher): CheckedTransactionResult = {
    var resultMsg = ""
    var result = false

    if(config.hasPreloadOfApi){
      val sig = t.getSignature
      val tOutSig = t.clearSignature //t.withSignature(null)
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
          resultMsg = s"The transaction(${t.id}) is not completed"
        }
      } catch {
        case e: RuntimeException => throw e
      }
    }else{
      result = true
    }

    CheckedTransactionResult(result, resultMsg)
  }

  protected def sendVoteMessage:Unit

  private def addTransToCache(t: Transaction) = {
    val checkedTransactionResult = checkTransaction(t, searcher)
    //签名验证成功
    val poolIsEmpty = if(pe.getRepChainContext.getTransactionPool.getCachePoolSize <= 0) true  else false
    if((checkedTransactionResult.result) &&  !pe.getRepChainContext.getTransactionPool.hasOverflowed ){
      pe.getRepChainContext.getTransactionPool.addTransactionToCache(t)
      RepLogger.trace(RepLogger.System_Logger,this.getLogMsgPrefix(s"${pe.getSysTag} trans pool recv,txid=${t.id}"))
      //广播接收交易事件
      //if (pe.getTransPoolMgr.getTransLength() >= SystemProfile.getMinBlockTransNum)
      if (poolIsEmpty)//加入交易之前交易池为空，发送抽签消息
        sendVoteMessage
    }
  }

  private def publishTrans(t: Transaction) = {
    if (this.addr4NonUser == "" && this.selfAddr.indexOf("/user") > 0) {
      this.addr4NonUser = this.selfAddr.substring(0, this.selfAddr.indexOf("/user"))
    }

    pe.getRepChainContext.getNodeMgr.getStableNodes.foreach(f => {
      if (this.addr4NonUser != "" && !NodeHelp.isSameNode(f.toString, this.addr4NonUser)) {
        visitStoreService(f, this.transPoolActorName, t)
      }

    })
  }

  override def receive = {
    //处理接收的交易
    case t: Transaction =>
      //保存交易到本地
      sendEvent(EventType.RECEIVE_INFO, mediator, pe.getSysTag, Topic.Transaction, Event.Action.TRANSACTION)
      addTransToCache(t)
    case _ => //ignore
  }
}
