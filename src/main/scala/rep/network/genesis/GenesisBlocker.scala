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

package rep.network.genesis

import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.pattern.{AskTimeoutException, ask}
import akka.util.Timeout
import rep.log.RepLogger
import rep.network.autotransaction.Topic
import rep.network.base.ModuleBase
import rep.network.consensus.util.BlockHelp
import rep.network.module.ModuleActorType
import rep.network.util.NodeHelp
import rep.network.consensus.common.MsgOfConsensus.{ConfirmedBlock, GenesisBlock, PreTransBlock, PreTransBlockResult}
import rep.proto.rc2.Block
import rep.storage.chain.block.BlockSearcher
import scala.concurrent._

/**
 * Created by jiangbuyun on 2020/03/19.
 * 建立创世块actor
 */
object GenesisBlocker {
  def props(name: String): Props = Props(classOf[GenesisBlocker], name)
}

class GenesisBlocker(moduleName: String) extends ModuleBase(moduleName) {

  import scala.concurrent.duration._

  val searcher: BlockSearcher = pe.getRepChainContext.getBlockSearch
  implicit val timeout = Timeout(pe.getRepChainContext.getTimePolicy.getTimeoutPreload*20.seconds)

  var preblock: Block = null

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "Block module start"))
  }

  

  private def ExecuteTransactionOfBlock(block: Block): Block = {
    try {
      //val future = pe.getActorRef(ActorType.preloaderoftransaction) ? PreTransBlock(block, "preload")
      val future = pe.getActorRef(ModuleActorType.ActorType.dispatchofpreload) ? PreTransBlock(block, "Genesis-preload")
      val result = Await.result(future, timeout.duration).asInstanceOf[PreTransBlockResult]
      if (result.result) {
        result.blc
      } else {
        null
      }
    } catch {
      case e: AskTimeoutException => null
    }finally {
      pe.getRepChainContext.freeBlockPreloadInstance("Genesis-preload")
    }
  }

  override def receive = {
    //创建块请求（给出块人）
    case GenesisBlock =>
      if(searcher.getChainInfo.height == 0 && NodeHelp.isSeedNode(pe.getSysTag,pe.getRepChainContext.getConfig.getGenesisNodeName)  ){
        if(this.preblock != null){
          //mediator ! Publish(Topic.Block, ConfirmedBlock(preblock, sender))
          pe.getRepChainContext.getCustomBroadcastHandler.PublishOfCustom(context,mediator,Topic.Block,ConfirmedBlock(preblock, sender))
        }else{
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "Create genesis block"))
          preblock = BlockHelp.CreateGenesisBlock(pe.getRepChainContext.getConfig)
          preblock = ExecuteTransactionOfBlock(preblock)
          if (preblock != null) {
            //preblock = BlockHelp.AddBlockHash(preblock)
            preblock = preblock.withHeader(BlockHelp.AddHeaderSignToBlock(preblock.getHeader, pe.getSysTag,pe.getRepChainContext.getSignTool))
            //sendEvent(EventType.RECEIVE_INFO, mediator, selfAddr, Topic.Block, Event.Action.BLOCK_NEW)
            //mediator ! Publish(Topic.Block, ConfirmedBlock(preblock, self))
            pe.getRepChainContext.getCustomBroadcastHandler.PublishOfCustom(context,mediator,Topic.Block,ConfirmedBlock(preblock, self))
            //getActorRef(pe.getSysTag, ActorType.PERSISTENCE_MODULE) ! BlockRestore(blc, SourceOfBlock.CONFIRMED_BLOCK, self)
          }
        }
      }

    case _ => //ignore
  }

}