package rep.sc

import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import akka.pattern.AskTimeoutException
import scala.concurrent._

import akka.actor.{ Actor, ActorRef, Props }
import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
import rep.app.conf.{ SystemProfile, TimePolicy }
import rep.network.base.ModuleBase
import rep.network.consensus.block.Blocker.{ PreTransBlock, PreTransBlockResult}
import rep.network.tools.PeerExtension
import rep.network.Topic
import rep.protos.peer._
import rep.sc.TransProcessor.DoTransaction
import rep.sc.Sandbox.DoTransactionResult
import rep.storage.{ ImpDataPreloadMgr }
import rep.utils.GlobalUtils.ActorType
import rep.utils._
import scala.collection.mutable
import akka.pattern.AskTimeoutException
import rep.crypto.Sha256
import rep.log.RepLogger
import akka.routing._;

object TransactionDispatcher {
  def props(name: String): Props = Props(classOf[TransactionDispatcher], name)
}

class TransactionDispatcher(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.collection.breakOut
  import scala.concurrent.duration._
  import scala.collection.immutable._

  private var TransActors : HashMap[String,ActorRef] = new HashMap[String,ActorRef]()

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "TransactionDispatcher Start"))
  }

  /** 根据合约的链码定义获得其唯一标示
   *  @param c 链码Id
   *  @return 链码id字符串
   */
  private def getChaincodeId(c: ChaincodeId): String={
    IdTool.getCid(c)
  }  
  /** 从部署合约的交易，获得其部署的合约的链码id
   *  @param t 交易对象
   *  @return 链码id
   */
  private def getTXCId(t: Transaction): String = {
    val t_cid = t.cid.get
    getChaincodeId(t_cid)
  } 
  
  private def HasTransActor(cid:String):Boolean = {
    if(this.TransActors.contains(cid)){
      true
    }else{
      false
    }
  }
  
  private def CheckTransActor(cid:String) : ActorRef = {
    if(HasTransActor(cid)){
      this.TransActors(cid)
    }else{
      val sd = context.actorOf( SandboxDispatcher.props("sandbox_dispatcher_"+cid,cid), "sandbox_dispatcher_"+cid )
      this.TransActors += cid -> sd
      sd
    }
  }
  
  override def receive = {
    case tr:DoTransaction =>
      if(tr.t != null){
        val ref : ActorRef = CheckTransActor(getTXCId(tr.t)) 
        ref.forward(tr)
      }else{
        RepLogger.error(RepLogger.Business_Logger, this.getLogMsgPrefix(s"recv DoTransaction is null"))
      }
    case _ => //ignore
  }
}
