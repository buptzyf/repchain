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

package rep.network.confirmblock.common

import akka.actor.{ActorRef, Props}
import akka.util.Timeout
import rep.app.conf.SystemProfile
import rep.log.{RepLogger, RepTimeTracer}
import rep.network.autotransaction.Topic
import rep.network.base.ModuleBase
import rep.network.confirmblock.IConfirmOfBlock
import rep.network.consensus.byzantium.ConsensusCondition
import rep.network.consensus.common.MsgOfConsensus.{BatchStore, BlockRestore, ConfirmedBlock}
import rep.network.consensus.util.BlockVerify
import rep.network.module.ModuleActorType
import rep.network.persistence.IStorager.SourceOfBlock
import rep.network.util.NodeHelp
import rep.utils.GlobalUtils.EventType


/**
 * Created by jiangbuyun on 2020/03/19.
 * 通用的确认块actor
 */

object ConfirmOfBlock {
  def props(name: String): Props = Props(classOf[ConfirmOfBlock], name)
}

class ConfirmOfBlock(moduleName: String) extends IConfirmOfBlock(moduleName) {
  import rep.protos.peer._

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("ConfirmOfBlock module start"))
    SubscribeTopic(mediator, self, selfAddr, Topic.Block, false)
  }

  override protected def handler(block: Block, actRefOfBlock: ActorRef) = {
    RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"confirm verify endorsement start,height=${block.height}"))
    if (SystemProfile.getIsVerifyOfEndorsement) {
      if (asyncVerifyEndorses(block)) {
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"confirm verify endorsement end,height=${block.height}"))
        //背书人的签名一致
        if (BlockVerify.verifySort(block.endorsements.toArray[Signature]) == 1 || (block.height == 1 && pe.getCurrentBlockHash == "" && block.previousBlockHash.isEmpty())) {
          //背书信息排序正确
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"confirm verify endorsement sort,height=${block.height}"))
          pe.getBlockCacheMgr.addToCache(BlockRestore(block, SourceOfBlock.CONFIRMED_BLOCK, actRefOfBlock))
          pe.getActorRef(ModuleActorType.ActorType.storager) ! BatchStore
          sendEvent(EventType.RECEIVE_INFO, mediator, pe.getSysTag, Topic.Block, Event.Action.BLOCK_NEW)
        } else {
          ////背书信息排序错误
        }
      } else {
        //背书验证有错误
      }
    } else {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"confirm verify endorsement sort,height=${block.height}"))
      pe.getBlockCacheMgr.addToCache(BlockRestore(block, SourceOfBlock.CONFIRMED_BLOCK, actRefOfBlock))
      pe.getActorRef(ModuleActorType.ActorType.storager) ! BatchStore
      sendEvent(EventType.RECEIVE_INFO, mediator, pe.getSysTag, Topic.Block, Event.Action.BLOCK_NEW)
    }
  }

  override protected def checkedOfConfirmBlock(block: Block, actRefOfBlock: ActorRef) = {
    if (pe.getCurrentBlockHash == "" && block.previousBlockHash.isEmpty()) {
      //if (NodeHelp.isSeedNode(pe.getNodeMgr.getStableNodeName4Addr(actRefOfBlock.path.address))) {
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"confirm verify blockhash,height=${block.height}"))
        handler(block, actRefOfBlock)
      //}else{
      //  RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"not confirm genesis block,blocker is not seed node,height=${block.height}"))
      //}
    } else {
      //与上一个块一致
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"confirm verify blockhash,height=${block.height}"))
        if (ConsensusCondition.ConsensusConditionChecked(block.endorsements.size)) {
          //符合大多数人背书要求
          handler(block, actRefOfBlock)
        } else {
          //错误，没有符合大多人背书要求。
        }
    }
  }

}