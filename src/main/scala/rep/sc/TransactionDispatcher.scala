package rep.sc


import akka.actor.{ ActorRef, Props}
import rep.network.base.ModuleBase
import rep.sc.SandboxDispatcher.DoTransaction
import rep.log.RepLogger

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
      if (tr.t != null) {
        val ref: ActorRef = CheckTransActor(IdTool.getTXCId(tr.t))
        ref.forward(tr)
      } else {
        RepLogger.error(RepLogger.Business_Logger, this.getLogMsgPrefix("recv DoTransaction is null"))
      }
    case _ => //ignore
  }
}
