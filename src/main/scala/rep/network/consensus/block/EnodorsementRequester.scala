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
import rep.network.consensus.endorse.EndorseMsg.{ EndorsementInfo, ResultOfEndorsed, RequesterOfEndorsement,ResultOfEndorseRequester}
import rep.network.tools.PeerExtension
import rep.network.Topic
import rep.protos.peer._
import rep.utils._
import rep.log.trace.LogType
import akka.pattern.AskTimeoutException
import rep.network.consensus.util.BlockVerify
import scala.util.control.Breaks
import rep.utils.GlobalUtils.EventType

object EnodorsementRequester {
  def props(name: String): Props = Props(classOf[EnodorsementRequester], name)
}

class EnodorsementRequester(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.collection.breakOut
  import scala.concurrent.duration._

  implicit val timeout = Timeout(TimePolicy.getTimeoutEndorse seconds)
  private val endorsementActorName = "/user/modulemanager/endorser"
  

  private var reqinfo: RequesterOfEndorsement = null
  private var reqresult :ResultOfEndorseRequester = null
  

  override def preStart(): Unit = {
    logMsg(LogType.INFO, "EnodorsementRequester Start")
  }

  private def sendMessage(addr: Address, data: EndorsementInfo) = {
    try {
      val selection: ActorSelection = context.actorSelection(toAkkaUrl(addr, endorsementActorName));
      selection ! data
    } catch {
      case re: RuntimeException   => logMsg(LogType.INFO, "send endorse request msg failed")
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
  
  private def handlerOfResult(r:ResultOfEndorsed)={
    if(this.reqinfo != null && this.reqinfo.blc.hashOfBlock.toStringUtf8() == r.BlockHash){
      if(this.reqresult == null){
        if (r.result) {
        if (EndorsementVerify(this.reqinfo.blc, r)) {
          logMsg(LogType.INFO, "endorse requester recv endorsement result ok")
          this.reqresult = ResultOfEndorseRequester(true, r.endor, r.BlockHash,this.reqinfo.endorer)
        } else {
          logMsg(LogType.INFO, "endorse requester recv endorsement result failed")
          this.reqresult = ResultOfEndorseRequester(false, r.endor, r.BlockHash,this.reqinfo.endorer)
        }
      } else {
        logMsg(LogType.INFO, "endorse requester recv endorsement result failed")
        this.reqresult = ResultOfEndorseRequester(false, r.endor, r.BlockHash,this.reqinfo.endorer)
      }
      }
      handler
    }
  }
  
  private def handler={
    if(this.reqresult == null){
      sendMessage(this.reqinfo.endorer, EndorsementInfo(this.reqinfo.blc, this.reqinfo.blocker))
    }else{
      context.parent ! this.reqresult
    }
  }

  override def receive = {
    case RequesterOfEndorsement(block, blocker, addr) =>
      if(this.reqinfo == null){
        this.reqinfo = RequesterOfEndorsement(block, blocker, addr)
        this.reqresult = null
      }else{
        if(this.reqinfo.blc.hashOfBlock.toStringUtf8() != block.hashOfBlock.toStringUtf8() ){
          this.reqinfo = RequesterOfEndorsement(block, blocker, addr)
          this.reqresult = null
        }
      }
      handler
      
    case ResultOfEndorsed(result, endor, blockhash)=>
      handlerOfResult(ResultOfEndorsed(result, endor, blockhash))
    case _ => //ignore
  }
}