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

import akka.actor.{ActorRef, Props}
import akka.pattern.{AskTimeoutException, ask}
import akka.util.Timeout
import rep.app.conf.{SystemProfile, TimePolicy}
import rep.log.{RepLogger, RepTimeTracer}
import rep.network.autotransaction.Topic
import rep.network.base.ModuleBase
import rep.network.consensus.common.MsgOfConsensus.{PreTransBlock, PreTransBlockResult}
import rep.network.consensus.pbft.MsgOfPBFT
import rep.network.consensus.pbft.MsgOfPBFT.{CollectEndorsement, VoteOfBlocker}
import rep.network.consensus.util.BlockHelp
import rep.network.module.ModuleActorType.ActorType
import rep.network.module.pbft.PBFTActorType
import rep.network.util.NodeHelp
import rep.protos.peer._
import rep.storage.ImpDataAccess
import rep.utils.GlobalUtils.EventType

import scala.concurrent._
import scala.util.control.Breaks._

object BlockerOfPBFT {
  def props(name: String): Props = Props(classOf[BlockerOfPBFT], name)
}

/**
 * 出块模块
 *
 * @author shidianyue
 * @version 1.0
 * @since 1.0
 * @param moduleName 模块名称
 */
class BlockerOfPBFT(moduleName: String) extends ModuleBase(moduleName) {

  import rep.protos.peer.Transaction

  import scala.collection.mutable.ArrayBuffer
  import scala.concurrent.duration._

  val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
  implicit val timeout = Timeout(TimePolicy.getTimeoutPreload.seconds)

  var preblock: Block = null

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("Block module start"))
    super.preStart()
  }

  private def CollectedTransOfBlock(start: Int, num: Int, limitsize: Int): ArrayBuffer[Transaction] = {
    var result = ArrayBuffer.empty[Transaction]
    try {
      val tmplist = pe.getTransPoolMgr.getTransListClone(start, num, pe.getSysTag)
      if (tmplist.size > 0) {
        val currenttime = System.currentTimeMillis() / 1000
        var transsize = 0
        breakable(
          tmplist.foreach(f => {
            transsize += f.toByteArray.size
            if (transsize * 3 > limitsize) {
              //区块的长度限制
              RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"block too length,txid=${f.id}" + "~" + selfAddr))
              break
            } else {
              f +=: result
            }
          }))
        if (result.isEmpty && tmplist.size >= SystemProfile.getMinBlockTransNum) {
          result = CollectedTransOfBlock(start + num, num, limitsize)
        }
      }
    } finally {
    }
    result
  }

  private def ExecuteTransactionOfBlock(block: Block): Block = {
    try {
      //val future = pe.getActorRef(ActorType.preloaderoftransaction) ? Blocker.PreTransBlock(block, "preload")
      val future = pe.getActorRef(ActorType.dispatchofpreload) ? PreTransBlock(block, "preload")
      val result = Await.result(future, timeout.duration).asInstanceOf[PreTransBlockResult]
      if (result.result) {
        result.blc
      } else {
        null
      }
    } catch {
      case e: AskTimeoutException => null
    }
  }

  private def CreateBlock(start: Int = 0): Block = {
    RepTimeTracer.setStartTime(pe.getSysTag, "Block", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)
    RepTimeTracer.setStartTime(pe.getSysTag, "createBlock", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)
    RepTimeTracer.setStartTime(pe.getSysTag, "collectTransToBlock", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)
    val trans = CollectedTransOfBlock(start, SystemProfile.getLimitBlockTransNum, SystemProfile.getBlockLength).reverse
    //todo 交易排序
    if (trans.size >= SystemProfile.getMinBlockTransNum) {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,CollectedTransOfBlock success,height=${pe.getBlocker.VoteHeight + 1},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
      RepTimeTracer.setEndTime(pe.getSysTag, "collectTransToBlock", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, trans.size)
      //此处建立新块必须采用抽签模块的抽签结果来进行出块，否则出现刚抽完签，马上有新块的存储完成，就会出现错误
      var blc = BlockHelp.WaitingForExecutionOfBlock(pe.getBlocker.voteBlockHash, pe.getBlocker.VoteHeight + 1, trans.toSeq)
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,height=${blc.height},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
      RepTimeTracer.setStartTime(pe.getSysTag, "PreloadTrans", System.currentTimeMillis(), blc.height, blc.transactions.size)
      blc = ExecuteTransactionOfBlock(blc)
      if (blc != null) {
        RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans", System.currentTimeMillis(), blc.height, blc.transactions.size)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,prelaod success,height=${blc.height},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
        blc = BlockHelp.AddBlockHash(blc)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,AddBlockHash success,height=${blc.height},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
        BlockHelp.AddSignToBlock(blc, pe.getSysTag)
      } else {
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("create new block error,preload error" + "~" + selfAddr))
        CreateBlock(start + trans.size)
      }
    } else {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("create new block error,trans count error" + "~" + selfAddr))
      null
    }
  }
  
  
  private def CreateBlock4One(start: Int = 0): Block = {
    RepTimeTracer.setStartTime(pe.getSysTag, "Block", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)
    RepTimeTracer.setStartTime(pe.getSysTag, "createBlock", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)
    RepTimeTracer.setStartTime(pe.getSysTag, "collectTransToBlock", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)
    val trans = CollectedTransOfBlock(start, SystemProfile.getLimitBlockTransNum, SystemProfile.getBlockLength).reverse.toSeq
    //todo 交易排序
    if (trans.size >= SystemProfile.getMinBlockTransNum) {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,CollectedTransOfBlock success,height=${pe.getBlocker.VoteHeight },local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
      RepTimeTracer.setEndTime(pe.getSysTag, "collectTransToBlock", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, trans.size)
      //此处建立新块必须采用抽签模块的抽签结果来进行出块，否则出现刚抽完签，马上有新块的存储完成，就会出现错误
      var blc = BlockHelp.WaitingForExecutionOfBlock(pe.getCurrentBlockHash, pe.getCurrentHeight + 1, trans)
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,height=${blc.height},local height=${pe.getCurrentHeight}" + "~" + selfAddr))
      RepTimeTracer.setStartTime(pe.getSysTag, "PreloadTrans", System.currentTimeMillis(), blc.height, blc.transactions.size)
      blc = ExecuteTransactionOfBlock(blc)
      if (blc != null) {
        RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans", System.currentTimeMillis(), blc.height, blc.transactions.size)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,prelaod success,height=${blc.height},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
        blc = BlockHelp.AddBlockHash(blc)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,AddBlockHash success,height=${blc.height},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
        BlockHelp.AddSignToBlock(blc, pe.getSysTag)
      } else {
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("create new block error,preload error" + "~" + selfAddr))
        CreateBlock(start + trans.size)
      }
    } else {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("create new block error,trans count error" + "~" + selfAddr))
      null
    }
  }

  private def CreateBlockHandler = {
    //if (preblock == null) {
    var blc : Block = null
    //if(SystemProfile.getNumberOfEndorsement == 1){
    //  blc = CreateBlock4One(0)
    //}else{
      blc = CreateBlock(0)
    //}
     
    if (blc != null) {
      RepTimeTracer.setEndTime(pe.getSysTag, "createBlock", System.currentTimeMillis(), blc.height, blc.transactions.size)
      this.preblock = blc
      schedulerLink = clearSched()

      // if (SystemProfile.getNumberOfEndorsement == 1) {
      //  pe.setCreateHeight(preblock.height)
      //  mediator ! Publish(Topic.Block, ConfirmedBlock(preblock, self))
      //}else{
        //在发出背书时，告诉对方我是当前出块人，取出系统的名称
        RepTimeTracer.setStartTime(pe.getSysTag, "Endorsement", System.currentTimeMillis(), blc.height, blc.transactions.size)
        val ar = pe.getActorRef(PBFTActorType.ActorType.endorsementcollectioner)
        //RepLogger.print(RepLogger.zLogger, pe.getSysTag + ", send CollectEndorsement to " + ar )
        ar ! CollectEndorsement(this.preblock, pe.getSysTag)
      //}
    } else {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("create new block error,CreateBlock is null" + "~" + selfAddr))
      pe.getActorRef(PBFTActorType.ActorType.voter) ! VoteOfBlocker("blocker")
    }
    //}
  }

  override def receive = {
    //创建块请求（给出块人）
    case MsgOfPBFT.CreateBlock =>
      // //RepLogger.print(RepLogger.zLogger,pe.getSysTag + ", Blocker recv CreateBlock: " + "Now blocker=" + pe.getBlocker.blocker)
      /*if(pe.getSysTag == "121000005l35120456.node1" &&  pe.count <= 10){
        pe.count = pe.count + 1
        throw new Exception("^^^^^^^^^^^^^^^^exception^^^^^^^^^^")
      }*/
      if (!pe.isSynching) {

        //
        /*if(SystemProfile.getNumberOfEndorsement == 1){
          if (NodeHelp.isBlocker(pe.getBlocker.blocker, pe.getSysTag)){
            sendEvent(EventType.PUBLISH_INFO, mediator, pe.getSysTag, Topic.Block, Event.Action.CANDIDATOR)
             if (preblock == null || (preblock.previousBlockHash.toStringUtf8() != pe.getCurrentBlockHash)) {
              //是出块节点
              CreateBlockHandler
             }
          }
          
        }else{ */
        ////RepLogger.print(RepLogger.zLogger, pe.getBlocker.voteBlockHash)
        ////RepLogger.print(RepLogger.zLogger, pe.getCurrentBlockHash)
        ////RepLogger.print(RepLogger.zLogger, if (preblock == null) null else preblock.previousBlockHash.toStringUtf8)
          if (NodeHelp.isBlocker(pe.getBlocker.blocker, pe.getSysTag)
            && pe.getBlocker.voteBlockHash == pe.getCurrentBlockHash) {
            sendEvent(EventType.PUBLISH_INFO, mediator, pe.getSysTag, Topic.Block, Event.Action.CANDIDATOR)

            //是出块节点
            if (preblock == null || (preblock.previousBlockHash.toStringUtf8() != pe.getBlocker.voteBlockHash)) {
              //RepLogger.print(RepLogger.zLogger, "CreateBlockHandler, " + "Me: "+pe.getSysTag)
              CreateBlockHandler
            }
          } else {
            //出块标识错误,暂时不用做任何处理
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,do not blocker or blocker hash not equal current hash,height=${pe.getCurrentHeight}" + "~" + selfAddr))
          }
        //}
      } else {
        //节点状态不对
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,node status error,status is synching,height=${pe.getCurrentHeight}" + "~" + selfAddr))
      }

    case _ => //ignore
  }

}