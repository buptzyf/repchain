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
import rep.network.consensus.endorse.EndorseMsg.{ VerifyResultOfEndorsement, verifyTransExeOfEndorsement,VerifyTypeOfEndorsement,VerifyCacher}
import rep.network.tools.PeerExtension
import rep.network.Topic
import rep.protos.peer._
import rep.utils._
import rep.log.trace.LogType
import akka.pattern.AskTimeoutException
import scala.util.control.Breaks
import rep.utils.GlobalUtils.EventType
import rep.network.consensus.block.Blocker.{PreTransBlock, PreTransBlockResult}
import rep.network.consensus.util.{ BlockVerify, BlockHelp }
import rep.utils.GlobalUtils.{ ActorType}

object VerifyTransOfExecutor {
  def props(name: String): Props = Props(classOf[VerifyTransOfExecutor], name)
}

class VerifyTransOfExecutor(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.collection.breakOut
  import scala.concurrent.duration._

  implicit val timeout = Timeout(TimePolicy.getTimeoutPreload seconds)
  var cache : VerifyCacher = null

  override def preStart(): Unit = {
    logMsg(LogType.INFO, "VerifyTransOfSigner Start")
  }

  private def ExecuteTransactionOfBlock(block: Block): Boolean = {
    try {
      val future = pe.getActorRef(ActorType.preloaderoftransaction) ? PreTransBlock(block,"endorser")
      val result = Await.result(future, timeout.duration).asInstanceOf[PreTransBlockResult]
      if (result.result) {
        var tmpblock = result.blc.withHashOfBlock(block.hashOfBlock)
        BlockVerify.VerifyHashOfBlock(tmpblock)
      } else {
        false
      }
    } catch {
      case e: AskTimeoutException => false
    }
  }

  private def Handler={
    if(this.cache.result == null){
      val result = ExecuteTransactionOfBlock(this.cache.blc)
      this.cache = VerifyCacher(this.cache.blc, this.cache.blocker,VerifyResultOfEndorsement(this.cache.blc.hashOfBlock.toStringUtf8(),this.cache.blocker,VerifyTypeOfEndorsement.transExeVerify,result))
    }
    sender ! this.cache.result
  }
  
  override def receive = {
    case verifyTransExeOfEndorsement(blc: Block, blocker: String) =>
      if(this.cache == null){
        logMsg(LogType.INFO, "trans exe verify is new")
        this.cache = VerifyCacher(blc, blocker,null)
      }else if(blc.hashOfBlock.toStringUtf8() == this.cache.blc.hashOfBlock.toStringUtf8() && blocker == this.cache.blocker){
        logMsg(LogType.INFO, "trans exe verify is exist")
      }else{
        logMsg(LogType.INFO, "trans exe verify is repeat")
        this.cache = VerifyCacher(blc, blocker,null)
      }
      Handler

    case _ => //ignore
  }
}