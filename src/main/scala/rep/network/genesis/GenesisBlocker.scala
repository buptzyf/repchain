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
import rep.app.conf.TimePolicy
import rep.log.RepLogger
import rep.network.autotransaction.Topic
import rep.network.base.ModuleBase
import rep.network.confirmblock.BoardcastConfirmBlock
import rep.network.consensus.util.BlockHelp
import rep.network.module.ModuleActorType
import rep.network.util.NodeHelp
import rep.protos.peer._
import rep.storage.ImpDataAccess
import rep.network.consensus.common.MsgOfConsensus.{ConfirmedBlock, GenesisBlock, PreTransBlock, PreTransBlockResult}

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

  val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
  implicit val timeout = Timeout(TimePolicy.getTimeoutPreload*20.seconds)

  var preblock: Block = null

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "Block module start"))
    //SubscribeTopic(mediator, self, selfAddr, Topic.Block, true)
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
    case GenesisBlock =>
      if(dataaccess.getBlockChainInfo().height == 0 && NodeHelp.isSeedNode(pe.getSysTag)  ){
        if(this.preblock != null){
          mediator ! Publish(Topic.Block, ConfirmedBlock(preblock, sender))
          var confirms = new BoardcastConfirmBlock(context,ConfirmedBlock(preblock, sender),pe.getNodeMgr.getNodes,pe.getNodeMgr.getStableNodes)
          var thread = new Thread(confirms)
          thread.start()
        }else{
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "Create genesis block"))
          preblock = BlockHelp.CreateGenesisBlock
          preblock = ExecuteTransactionOfBlock(preblock)
          if (preblock != null) {
            preblock = BlockHelp.AddBlockHash(preblock)
            preblock = BlockHelp.AddSignToBlock(preblock, pe.getSysTag)
            //sendEvent(EventType.RECEIVE_INFO, mediator, selfAddr, Topic.Block, Event.Action.BLOCK_NEW)
            mediator ! Publish(Topic.Block, ConfirmedBlock(preblock, self))
            //getActorRef(pe.getSysTag, ActorType.PERSISTENCE_MODULE) ! BlockRestore(blc, SourceOfBlock.CONFIRMED_BLOCK, self)
          } 
        }
        
      }

    case _ => //ignore
  }

}