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

package rep.network.sync.response

import akka.actor.Props
import rep.log.RepLogger
import rep.network.base.ModuleBase
import rep.network.sync.SyncMsg
import rep.network.sync.SyncMsg.{BlockDataOfRequest, ChainInfoOfRequest, ResponseInfo}
import rep.network.util.NodeHelp
import rep.protos.peer._
import rep.storage.{ImpDataAccess, JsonUtil}
import rep.utils.GlobalUtils.{BlockEvent, EventType}
import rep.network.tools.transpool.TransactionPoolMgr

/**
 * Created by jiangbuyun on 2020/03/19.
 * 区块同步时，接收请求区块信息的actor
 */

object SynchronizeResponser {
  def props(name: String): Props = Props(classOf[SynchronizeResponser], name)
}

class SynchronizeResponser(moduleName: String) extends ModuleBase(moduleName) {

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix( "SynchronizeResponse start"))
  }

  val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)

  override def receive: Receive = {
    case ChainInfoOfRequest(height) =>
      sendEvent(EventType.RECEIVE_INFO, mediator,pe.getSysTag, BlockEvent.CHAIN_INFO_SYNC,  Event.Action.BLOCK_SYNC)
      if (NodeHelp.isSameNodeForRef(sender(), self)) {
        RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(  "recv sync chaininfo request,it is self,do not response, from actorAddr" + "～" + NodeHelp.getNodePath(sender())))
      } //else {
        RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix( "recv sync chaininfo request from actorAddr" + "～" + NodeHelp.getNodePath(sender())))

        val ti0 = System.currentTimeMillis()
        val responseInfo = dataaccess.getBlockChainInfo()
        RepLogger.debug(RepLogger.zLogger, "getBlockChainInfo,time=" + (System.currentTimeMillis() - ti0))
        var ChainInfoOfSpecifiedHeight : BlockchainInfo = BlockchainInfo(0l, 0l, _root_.com.google.protobuf.ByteString.EMPTY,
                                                                        _root_.com.google.protobuf.ByteString.EMPTY,
                                                                        _root_.com.google.protobuf.ByteString.EMPTY)
        if(height >0 && height < responseInfo.height){
          val ti1 = System.currentTimeMillis()
          val b = dataaccess.getBlock4ObjectByHeight(height)
          RepLogger.debug(RepLogger.zLogger, "getBlock4ObjectByHeight,time=" + (System.currentTimeMillis() - ti1) + "," + pe.getTransPoolMgr.getTransLength())
          RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(  s"node number:${pe.getSysTag},recv synch chaininfo request,request height:${height},local chaininof=${responseInfo.height}"))
          ChainInfoOfSpecifiedHeight = ChainInfoOfSpecifiedHeight.withHeight(height)
          ChainInfoOfSpecifiedHeight = ChainInfoOfSpecifiedHeight.withCurrentBlockHash(b.hashOfBlock)
          ChainInfoOfSpecifiedHeight = ChainInfoOfSpecifiedHeight.withPreviousBlockHash(b.previousBlockHash)
          ChainInfoOfSpecifiedHeight = ChainInfoOfSpecifiedHeight.withCurrentStateHash(b.stateHash)
        }
        sender ! ResponseInfo(responseInfo,self,ChainInfoOfSpecifiedHeight,pe.getSysTag)
      //}

    case BlockDataOfRequest(startHeight) =>
      RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(  s"node number:${pe.getSysTag},start block number:${startHeight},Get a data request from  $sender" + "～" + selfAddr))
      val local = dataaccess.getBlockChainInfo()
      var data = Block()
      if (local.height >= startHeight) {
        data = dataaccess.getBlock4ObjectByHeight(startHeight)
        sender  ! SyncMsg.BlockDataOfResponse(data)
      }
      sendEvent(EventType.PUBLISH_INFO, mediator,pe.getSysTag, pe.getNodeMgr.getNodeName4AddrString(NodeHelp.getNodeAddress(sender)), Event.Action.BLOCK_SYNC_DATA)
  }

}