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

package rep.network.consensus.pbft.block

import akka.util.Timeout
import akka.pattern.ask
import akka.pattern.AskTimeoutException

import scala.concurrent._
import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import rep.network.base.ModuleBase
import rep.network.consensus.util.BlockHelp
import rep.network.util.NodeHelp
import rep.log.RepLogger
import rep.network.autotransaction.Topic
import rep.network.confirmblock.pbft.ConfirmOfBlockOfPBFT
import rep.network.consensus.common.MsgOfConsensus.{PreTransBlock, PreTransBlockResult}
import rep.network.consensus.pbft.MsgOfPBFT
import rep.network.consensus.pbft.MsgOfPBFT.ConfirmedBlock
import rep.network.module.ModuleActorType
import rep.proto.rc2.Block
import rep.storage.chain.block.BlockSearcher

object GenesisBlocker {
  def props(name: String): Props = Props(classOf[GenesisBlocker], name)

  case object GenesisBlock

}

/**
 * 出块模块
 *
 * @author shidianyue
 * @version 1.0
 * @since 1.0
 * @param moduleName 模块名称
 */
class GenesisBlocker(moduleName: String) extends ModuleBase(moduleName) {

  import context.dispatcher
  import scala.concurrent.duration._
  implicit val timeout = Timeout(pe.getRepChainContext.getTimePolicy.getTimeoutPreload*20.seconds)
  val searcher = pe.getRepChainContext.getBlockSearch

  var preblock: Block = null

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "Block module start"))
    SubscribeTopic(mediator, self, selfAddr, Topic.Block, true)
  }

  

  private def ExecuteTransactionOfBlock(block: Block): Block = {
    try {
      //val future = pe.getActorRef(ActorType.preloaderoftransaction) ? PreTransBlock(block, "preload")
      val future = pe.getActorRef(ModuleActorType.ActorType.dispatchofpreload) ? PreTransBlock(block, "preload")
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

  override def receive = {
    //创建块请求（给出块人）
    case GenesisBlocker.GenesisBlock =>
      RepLogger.debug(RepLogger.zLogger,"R: " + ConfirmOfBlockOfPBFT.nn(sender) + "->" + ConfirmOfBlockOfPBFT.nn(pe.getSysTag) + ", GenesisBlock: ")
      if(searcher.getChainInfo.height == 0 && NodeHelp.isSeedNode(pe.getSysTag,pe.getRepChainContext.getConfig.getGenesisNodeName)  ){
        if(this.preblock != null){
          mediator ! Publish(Topic.Block, MsgOfPBFT.ConfirmedBlock(preblock, sender, Seq.empty))
          //pe.getRepChainContext.getCustomBroadcastHandler.BroadcastConfirmBlock(context,mediator,MsgOfPBFT.ConfirmedBlock(preblock, sender, Seq.empty))
        }else{
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "Create genesis block"))
          preblock = BlockHelp.CreateGenesisBlock(pe.getRepChainContext.getConfig)
          preblock = ExecuteTransactionOfBlock(preblock)
          if (preblock != null) {
            preblock = BlockHelp.AddBlockHeaderHash(preblock,pe.getRepChainContext.getHashTool)
            preblock = preblock.withHeader(BlockHelp.AddHeaderSignToBlock(preblock.getHeader, pe.getSysTag,pe.getRepChainContext.getSignTool))
            //sendEvent(EventType.RECEIVE_INFO, mediator, selfAddr, Topic.Block, Event.Action.BLOCK_NEW)
            mediator ! Publish(Topic.Block, ConfirmedBlock(preblock, self, Seq.empty))
            //pe.getRepChainContext.getCustomBroadcastHandler.BroadcastConfirmBlock(context,mediator,ConfirmedBlock(preblock, self, Seq.empty))
            //getActorRef(pe.getSysTag, ActorType.PERSISTENCE_MODULE) ! BlockRestore(blc, SourceOfBlock.CONFIRMED_BLOCK, self)
          } 
        }
        
      }

    case _ => //ignore
  }

}