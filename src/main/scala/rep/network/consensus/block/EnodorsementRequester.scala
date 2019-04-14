package rep.network.consensus.block

import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import akka.pattern.AskTimeoutException
import scala.concurrent._

import akka.actor.{ Actor, ActorRef, Props, Address, ActorSelection }
import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
import rep.app.conf.{ SystemProfile, TimePolicy }
import rep.network.base.ModuleBase
import rep.network.consensus.block.Blocker.{ EndorsementInfo, ResultOfEndorsed, RequesterOfEndorsement,ResultOfEndorseRequester}
import rep.network.tools.PeerExtension
import rep.network.Topic
import rep.protos.peer._
import rep.utils._
import rep.log.trace.LogType
import akka.pattern.AskTimeoutException
import rep.network.consensus.util.BlockVerify
import scala.util.control.Breaks

object EnodorsementRequester {
  def props(name: String): Props = Props(classOf[EnodorsementRequester], name)
}

class EnodorsementRequester(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.collection.breakOut
  import scala.concurrent.duration._

  implicit val timeout = Timeout(TimePolicy.getTimeoutEndorse seconds)
  private val endorsementActorName = "/user/modulemanager/endorser"

  private var requesterOfCollection: ActorRef = null

  override def preStart(): Unit = {
    logMsg(LogType.INFO, "EndorseCollector Start")
  }

  private def sendMessage(addr: Address, data: EndorsementInfo): ResultOfEndorsed = {
    try {
      val selection: ActorSelection = context.actorSelection(toAkkaUrl(addr, endorsementActorName));
      val future = selection ? data
      Await.result(future, timeout.duration).asInstanceOf[ResultOfEndorsed]
    } catch {
      case e: AskTimeoutException => ResultOfEndorsed(false, null, data.blc.hashOfBlock.toStringUtf8())
      case re: RuntimeException   => ResultOfEndorsed(false, null, data.blc.hashOfBlock.toStringUtf8())
    }
  }

  private def toAkkaUrl(addr: Address, actorName: String): String = {
    return addr.toString + "/" + actorName;
  }

  private def EndorsementVerify(block: Block, result: ResultOfEndorsed): Boolean = {
    val bb = block.clearEndorsements.toByteArray
    val ev = BlockVerify.VerifyOneEndorseOfBlock(result.endor, bb, pe.getSysTag)
    if (ev._1) {
      true
    } else {
      false
    }
  }

  override def receive = {
    case RequesterOfEndorsement(block, blocker, addr) =>
      val result = sendMessage(addr, EndorsementInfo(block, blocker))
      if (result.result) {
        if (EndorsementVerify(block, result)) {
          context.parent ! ResultOfEndorseRequester(result.result, result.endor, result.BlockHash,addr)
        } else {
          context.parent ! ResultOfEndorseRequester(false, result.endor, result.BlockHash,addr)
        }
      } else {
        context.parent ! ResultOfEndorseRequester(false, result.endor, result.BlockHash,addr)
      }

    case _ => //ignore
  }
}