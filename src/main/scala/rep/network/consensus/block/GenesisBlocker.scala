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

package rep.network.consensus.block

import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import akka.pattern.AskTimeoutException
import scala.concurrent._

import akka.actor.{ ActorRef, Address, Props }
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import com.google.protobuf.ByteString
import rep.app.conf.{ SystemProfile, TimePolicy }
import rep.crypto.Sha256
import rep.network._
import rep.network.base.ModuleBase
import rep.network.consensus.block.Blocker.{ConfirmedBlock,PreTransBlock,PreTransBlockResult}
import rep.protos.peer._
import rep.storage.ImpDataAccess
import rep.utils.GlobalUtils.{ ActorType, BlockEvent, EventType, NodeStatus }
import scala.collection.mutable
import com.sun.beans.decoder.FalseElementHandler
import scala.util.control.Breaks
import rep.utils.IdTool
import scala.util.control.Breaks._
import rep.network.consensus.util.{ BlockHelp, BlockVerify }
import rep.network.util.NodeHelp
import rep.log.RepLogger

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
  import akka.actor.ActorSelection
  import scala.collection.mutable.ArrayBuffer
  import rep.protos.peer.{ Transaction }

  val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
  implicit val timeout = Timeout(TimePolicy.getTimeoutPreload*20 seconds)

  var preblock: Block = null

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "Block module start"))
    SubscribeTopic(mediator, self, selfAddr, Topic.Block, true)
  }

  

  private def ExecuteTransactionOfBlock(block: Block): Block = {
    try {
      //val future = pe.getActorRef(ActorType.preloaderoftransaction) ? PreTransBlock(block, "preload")
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

  override def receive = {
    //创建块请求（给出块人）
    case GenesisBlocker.GenesisBlock =>
      if(dataaccess.getBlockChainInfo().height == 0 && NodeHelp.isSeedNode(pe.getSysTag)  ){
        if(this.preblock != null){
          mediator ! Publish(Topic.Block, ConfirmedBlock(preblock, sender))
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