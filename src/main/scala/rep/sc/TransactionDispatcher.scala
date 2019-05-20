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
import rep.network.consensus.block.Blocker.{ PreTransBlock, PreTransBlockResult }
import rep.network.tools.PeerExtension
import rep.network.Topic
import rep.protos.peer._
import rep.sc.Sandbox.DoTransactionResult
import rep.sc.SandboxDispatcher.DoTransaction
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
  import rep.utils.IdTool

  private var TransActors: HashMap[String, ActorRef] = new HashMap[String, ActorRef]()

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("TransactionDispatcher Start"))
  }

  private def HasTransActor(cid: String): Boolean = {
    this.TransActors.contains(cid)
  }

  private def CheckTransActor(cid: String): ActorRef = {
    if (HasTransActor(cid)) {
      RepLogger.debug(RepLogger.Sandbox_Logger, s"sandbox dispatcher for ${cid} is exist.")
      this.TransActors(cid)
    } else {
      val sd = context.actorOf(SandboxDispatcher.props("sandbox_dispatcher_" + cid, cid), "sandbox_dispatcher_" + cid)
      this.TransActors += cid -> sd
      RepLogger.debug(RepLogger.Sandbox_Logger, s"create sandbox dispatcher for ${cid} .")
      sd
    }
  }

  override def receive = {
    case tr: DoTransaction =>
      if (tr.t != null) {
        val ref: ActorRef = CheckTransActor(IdTool.getTXCId(tr.t))
        ref.forward(tr)
      } else {
        RepLogger.error(RepLogger.Business_Logger, this.getLogMsgPrefix("recv DoTransaction is null"))
      }
    case _ => //ignore
  }
}
