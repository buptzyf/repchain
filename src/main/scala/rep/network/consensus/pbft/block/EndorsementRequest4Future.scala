/*
 * Copyright  2019 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BA SIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
//zhj
package rep.network.consensus.pbft.block

import akka.pattern.ask
import akka.actor.{ActorSelection, Address, Props}
import akka.pattern.AskTimeoutException
import akka.util.Timeout
import com.google.protobuf.ByteString
import rep.app.Repchain
import rep.app.conf.{SystemProfile, TimePolicy}
import rep.log.RepLogger
import rep.network.base.ModuleBase
import rep.network.consensus.pbft.MsgOfPBFT
import rep.network.consensus.pbft.MsgOfPBFT.{MsgPbftPrePrepare, MsgPbftPrePrepareResend, MsgPbftReply, MsgPbftReplyOk, RequesterOfEndorsement, ResendEndorseInfo, ResultFlagOfEndorse, ResultOfEndorsed}
import rep.network.consensus.util.BlockVerify
import rep.protos.peer._

import scala.concurrent._

object EndorsementRequest4Future {
  def props(name: String): Props = Props(classOf[EndorsementRequest4Future], name)
}

class EndorsementRequest4Future(moduleName: String) extends ModuleBase(moduleName) {
  import scala.concurrent.duration._
  //case class HashReply(hash:ByteString, reply:MPbftReply)
  private var recvedHash : ByteString = null
  private var recvedReplies = scala.collection.mutable.Buffer[MPbftReply]()
  //private var recvedRepliesCount = scala.collection.mutable.HashMap[ByteString, Int]()

  implicit val timeout = Timeout(TimePolicy.getTimeoutEndorse.seconds)
  //private val endorsementActorName = "/user/modulemanager/endorser"
  private val endorsementActorName = "/user/modulemanager/dispatchofRecvendorsement"

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "EndorsementRequest4Future Start"))
  }

  private def toAkkaUrl(addr: Address, actorName: String): String = {
    return addr.toString + "/" + actorName;
  }


  private def handler(reqinfo: RequesterOfEndorsement) = {
      schedulerLink = clearSched()
      try {
        val selection: ActorSelection = context.actorSelection(toAkkaUrl(reqinfo.endorer, endorsementActorName));
        val data = MsgPbftPrePrepare("", reqinfo.blc, reqinfo.blocker)

        selection ! data
      }  catch {
        case e: AskTimeoutException =>

        case te: TimeoutException =>
      }
  }

  //reply start-------------------------------------------
  private def VerifyReply(block: Block, reply: MPbftReply): Boolean = {
    val bb = reply.clearSignature.toByteArray
    val signature = reply.signature.get//todo get?
    val ev = BlockVerify.VerifyOneEndorseOfBlock(signature, bb, pe.getSysTag)
    ev._1
  }

  private def ProcessMsgPbftReply(reply: MsgPbftReply){
    if (VerifyReply(reply.block,reply.reply)) {
      val hash = reply.block.hashOfBlock
      if ( hash.equals(recvedHash)) {
        recvedReplies += reply.reply
      } else {
        recvedHash = hash
        recvedReplies.clear()
        recvedReplies += reply.reply
      }
      if (recvedReplies.size >= (SystemProfile.getPbftF + 1)) {
        val replies = recvedReplies
          .sortWith( (left,right)=> left.signature.get.certId.toString < right.signature.get.certId.toString)
        context.parent ! MsgPbftReplyOk(reply.block, replies)
        recvedHash = null
        recvedReplies.clear()
      }
    }
  }
  //reply end-------------------------------------------

  override def receive = {
    case MsgPbftReply(block,reply,chainInfo) =>
      RepLogger.debug(RepLogger.zLogger,"R: " + Repchain.nn(sender) + "->" + Repchain.nn(pe.getSysTag) + ", MsgPbftReply: " + ", " + Repchain.h4(block.hashOfBlock.toStringUtf8))
      ProcessMsgPbftReply(MsgPbftReply(block,reply,chainInfo))

    case MsgPbftPrePrepareResend(senderPath,block, blocker) =>
      sender ! MsgPbftPrePrepare(senderPath,block, blocker)

    case RequesterOfEndorsement(block, blocker, addr) =>
      //待请求背书的块的上一个块的hash不等于系统最新的上一个块的hash，停止发送背书
      RepLogger.debug(RepLogger.zLogger,"R: " + Repchain.nn(sender) + "->" + Repchain.nn(pe.getSysTag) + ", RequesterOfEndorsement: " + ", " + Repchain.h4(block.hashOfBlock.toStringUtf8))
      if(block.previousBlockHash.toStringUtf8() == pe.getCurrentBlockHash){
        handler(RequesterOfEndorsement(block, blocker, addr))
      }else{
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"--------endorsementRequest4Future back out  endorsement,prehash not equal pe.currenthash ,height=${block.height},local height=${pe.getCurrentHeight} "))
      }

    case _ => //ignore
  }
}