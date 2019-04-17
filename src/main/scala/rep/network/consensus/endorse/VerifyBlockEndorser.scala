package rep.network.consensus.endorse

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
import rep.network.consensus.endorse.EndorseMsg.{ VerifyResultOfEndorsement, VerfiyBlockEndorseOfEndorsement,VerifyTypeOfEndorsement,VerifyCacher}
import rep.network.tools.PeerExtension
import rep.network.Topic
import rep.protos.peer._
import rep.utils._
import rep.log.trace.LogType
import akka.pattern.AskTimeoutException
import rep.network.consensus.util.BlockVerify
import scala.util.control.Breaks
import rep.utils.GlobalUtils.EventType

object VerifyBlockEndorser {
  def props(name: String): Props = Props(classOf[VerifyBlockEndorser], name)
}

class VerifyBlockEndorser(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.collection.breakOut
  import scala.concurrent.duration._

  var cache : VerifyCacher = null

  override def preStart(): Unit = {
    logMsg(LogType.INFO, "VerifyTransOfSigner Start")
  }

  private def Handler={
    if(this.cache.result == null){
      val result = BlockVerify.VerifyAllEndorseOfBlock(this.cache.blc, pe.getSysTag)
      this.cache = VerifyCacher(this.cache.blc, this.cache.blocker,VerifyResultOfEndorsement(this.cache.blc.hashOfBlock.toStringUtf8(),this.cache.blocker,VerifyTypeOfEndorsement.endorsementVerify,result._1))
    }
    sender ! this.cache.result
  }

  override def receive = {
    case VerfiyBlockEndorseOfEndorsement(blc: Block, blocker: String) =>
      if(this.cache == null){
        logMsg(LogType.INFO, "endorse sign verify is new")
        this.cache = VerifyCacher(blc, blocker,null)
      }else if(blc.hashOfBlock.toStringUtf8() == this.cache.blc.hashOfBlock.toStringUtf8() && blocker == this.cache.blocker){
        logMsg(LogType.INFO, "endorse sign verify is exist")
      }else{
        logMsg(LogType.INFO, "endorse sign verify is repeat")
        this.cache = VerifyCacher(blc, blocker,null)
      }
      Handler

    case _ => //ignore
  }
}