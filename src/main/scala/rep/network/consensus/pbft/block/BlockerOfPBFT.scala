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
import rep.app.Repchain
import rep.log.{RepLogger, RepTimeTracer}
import rep.network.autotransaction.Topic
import rep.network.consensus.common.block.IBlocker
import rep.network.consensus.pbft.MsgOfPBFT
import rep.network.consensus.pbft.MsgOfPBFT.{CollectEndorsement, VoteOfBlocker}
import rep.network.module.pbft.PBFTActorType
import rep.network.util.NodeHelp
import rep.proto.rc2.{Block, Event, Transaction}
import rep.utils.GlobalUtils.EventType

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
class BlockerOfPBFT(moduleName: String) extends IBlocker(moduleName) {
  import scala.collection.mutable.ArrayBuffer
  import scala.concurrent.duration._


  var preblock: Block = null

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("Block module start"))
    super.preStart()
  }

  private def CreateBlockHandler = {
    var blc : Block = null

    if (blc ==null)
      blc = PackedBlock(0)

    if (blc != null) {
      RepTimeTracer.setEndTime(pe.getSysTag, "createBlock", System.currentTimeMillis(), blc.getHeader.height, blc.transactions.size)
      this.preblock = blc
      RepLogger.debug(RepLogger.zLogger, pe.getSysTag + ", preblock= " + preblock.getHeader.height + "," +Repchain.h4(preblock.getHeader.hashPresent.toStringUtf8) )
      schedulerLink = clearSched()

      // if (SystemProfile.getNumberOfEndorsement == 1) {
      //  pe.setCreateHeight(preblock.height)
      //  mediator ! Publish(Topic.Block, ConfirmedBlock(preblock, self))
      //}else{
        //在发出背书时，告诉对方我是当前出块人，取出系统的名称
        RepTimeTracer.setStartTime(pe.getSysTag, "Endorsement", System.currentTimeMillis(), blc.getHeader.height, blc.transactions.size)
        val ar = pe.getActorRef(PBFTActorType.ActorType.endorsementcollectioner)
        RepLogger.debug(RepLogger.zLogger, pe.getSysTag + ", send CollectEndorsement to " + ar )
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
      RepLogger.debug(RepLogger.zLogger,"R: " + Repchain.nn(sender) + "->" + Repchain.nn(pe.getSysTag) + ", CreateBlock: " + Repchain.nn(pe.getBlocker.blocker))
      if (!pe.isSynching) {
          if (NodeHelp.isBlocker(pe.getBlocker.blocker, pe.getSysTag)
            && pe.getBlocker.voteBlockHash == pe.getCurrentBlockHash) {
            sendEvent(EventType.PUBLISH_INFO, mediator, pe.getSysTag, Topic.Block, Event.Action.CANDIDATOR)

            //是出块节点
            if (preblock == null || (preblock.getHeader.hashPrevious.toStringUtf8() != pe.getBlocker.voteBlockHash)) {
              RepLogger.debug(RepLogger.zLogger, "CreateBlockHandler, " + "Me: "+Repchain.nn(pe.getSysTag))
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