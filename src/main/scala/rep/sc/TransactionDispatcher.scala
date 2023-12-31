package rep.sc


import akka.actor.{ActorRef, Props}
import rep.network.base.ModuleBase
import rep.sc.SandboxDispatcher.{DoTransaction, DoTransactionOfCache}
import rep.log.{RepLogger, RepTimeTracer}

object TransactionDispatcher {
  def props(name: String): Props = Props(classOf[TransactionDispatcher], name)
}

class TransactionDispatcher(moduleName: String) extends ModuleBase(moduleName) {
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
      RepLogger.debug(RepLogger.Sandbox_Logger, s"transaction dispatcher for ${cid} is exist.")
      this.TransActors(cid)
    } else {
      val sd = context.actorOf(SandboxDispatcher.props("sandbox_dispatcher_" + cid, cid), "sandbox_dispatcher_" + cid)
      this.TransActors += cid -> sd
      RepLogger.debug(RepLogger.Sandbox_Logger, s"create transaction dispatcher for ${cid} .")
      sd
    }
  }

  override def receive = {
    case tr: DoTransaction =>
      RepTimeTracer.setStartTime(pe.getSysTag, "transaction-dispatcher", System.currentTimeMillis(), pe.getCurrentHeight+1, tr.ts.length)
      if (tr.ts != null && tr.ts.length > 0) {
        val ref: ActorRef = CheckTransActor(IdTool.getTXCId(tr.ts(0)))
        ref.forward(tr)
      } else {
        RepLogger.error(RepLogger.Business_Logger, this.getLogMsgPrefix("recv DoTransaction is null"))
      }
    case trOfCache:DoTransactionOfCache=>
      val ts = pe.getTrans(trOfCache.cacheIdentifier)
      if(ts != null){
        RepTimeTracer.setStartTime(pe.getSysTag, "transaction-dispatcher", System.currentTimeMillis(), pe.getCurrentHeight+1, ts.length)
        if ( ts.length > 0) {
          val ref: ActorRef = CheckTransActor(IdTool.getTXCId(ts(0)))
          ref.forward(trOfCache)
        } else {
          RepLogger.error(RepLogger.Business_Logger, this.getLogMsgPrefix("recv DoTransaction is null"))
        }
      }else{
        RepLogger.error(RepLogger.Business_Logger, this.getLogMsgPrefix("recv trans of cache is null"))
      }


    case _ => //ignore
  }
}
