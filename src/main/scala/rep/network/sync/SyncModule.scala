/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Fintech Research Center of ISCAS.
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
 */

package rep.network.sync

import akka.actor.{ ActorRef, Props }
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import rep.app.conf.TimePolicy
import rep.network.base.ModuleBase
import rep.network.consensus.vote.CRFDVoterModule.SeedNode
import rep.network.consensus.block.BlockHelper
import rep.network.persistence.PersistenceModule
import rep.network.persistence.PersistenceModule.{ BlockRestore, BlockSrc, LastBlock }
import rep.network.sync.SyncModule._
import rep.protos.peer.{ Block, BlockchainInfo }
import rep.storage.ImpDataAccess
//import rep.utils.GlobalUtils.{ ActorType, BlockEvent }
import rep.utils.GlobalUtils.{ ActorType, BlockEvent, EventType,BlockChainStatus }
//import rep.network.Topic
import rep.protos.peer.Event
import scala.collection.immutable
import scala.collection.mutable
import rep.network.Topic
import scala.util.control.Breaks



/**
 * Created by shidianyue on 2017/9/22.
 * 
 * @update 2018-05 jiangbuyun
 */
object SyncModule {
  def props(name: String): Props = Props(classOf[SyncModule], name)

  case object ChainInfoStatus {
    val START_CHAININFO = "start"
    val END_CHAININFO = "end"
    val READY_CHAININFO = "ready"
  }
  
  case class ChainInfoReq(info: BlockchainInfo, actorAddr: String)

  case class ChainInfoRes(src: BlockchainInfo,
    response: BlockchainInfo, isSame: Boolean,
    addr: String, addActor: ActorRef)

  case class ChainSyncReq(blocker: ActorRef)

  case class ChainDataReq(receiver: ActorRef,
    height: Long, blkType: String)

  case class ChainDataReqSingleBlk(receiver: ActorRef,
    targetHeight: Long)

  case class ChainDataRes(data: Array[Block])

  case class ChainDataResSingleBlk(data: Block, targetHeight: Long)

  case class SyncResult(result: Boolean, isStart: Boolean, isStartNode: Boolean, lastBlkHash: String)

  //同步超时
  case object SyncTimeOut

  //When system setup
  case object SetupSync

}

class SyncModule(moduleName: String) extends ModuleBase(moduleName) {

  import context.dispatcher
  import scala.concurrent.duration._
  import scala.util.control.Breaks._

  val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)

  var isStart = false
  //现阶段出块同步都是向出块人，所以没有用
  //Cache sync result
  var syncResults = new immutable.TreeMap[Long, Seq[ChainInfoRes]]()
  var syncSameCount = 0
  var syncMaxCount = 0
  var syncMaxHight: Long = 0
  var isSyncReq = false
  var IsFinishedChainInfo = ChainInfoStatus.READY_CHAININFO

  override def preStart(): Unit = {
    SubscribeTopic(mediator, self, selfAddr, BlockEvent.CHAIN_INFO_SYNC, false)
  }

  def clearCache(): Unit = {
    syncResults =  new immutable.TreeMap[Long, Seq[ChainInfoRes]]()
    syncMaxHight = 0
    syncSameCount = 0
    syncMaxCount = 0
    isStart = false
    IsFinishedChainInfo = ChainInfoStatus.READY_CHAININFO
  }

  def greaterThanSelfForCount(cheight:Long):Long={
    var c : Long = -1
    if(!syncResults.isEmpty){
      val source1 : Array[Long]  = syncResults.keys.toArray
      var tmpHeightCounter :immutable.TreeMap[Long, Long] =  new immutable.TreeMap[Long, Long]()
      val loopbreak = new Breaks
      
      for(i <- (source1.length-1) to 0 by -1){
        //if(source1(i) > cheight){
            val a : Seq[ChainInfoRes] = syncResults(source1(i))
            var counterheight : Long = 0l
            var previousHeight : Long = 0l
            if(tmpHeightCounter.contains(source1(i)+1)){
              previousHeight = tmpHeightCounter(source1(i)+1)
            }
            counterheight = previousHeight + a.size
            tmpHeightCounter += source1(i) -> counterheight
          //}
        }
      
      if(!tmpHeightCounter.isEmpty){
        val b : Array[Long] = tmpHeightCounter.keys.toArray
        loopbreak.breakable(
            for(i <- (b.length-1) to 0 by -1){
              val h = b(i)
              val v = tmpHeightCounter(h)
              if((v * 2) >= (pe.getNodes.size - 1)){
                c = h
                loopbreak.break
              }
            }
        )
      }
    }
    c
  }
  
  def sendSyncDataReq(syncheight:Long)={
    if (!isSyncReq) {
      isSyncReq = true
      val re = syncResults(syncheight)
      val h = dataaccess.getBlockChainInfo().height
      logMsg(LOG_TYPE.INFO, moduleName, s"node number:${pe.getSysTag},start request data from height:${h},Get a data request from  $sender", selfAddr)
      re.head.addActor ! ChainDataReq(self,
        h, BlockSrc.SYNC_START_BLOCK)
    }
  }
  
  def syncFinishHandle(src: BlockchainInfo) = {
    logMsg(LOG_TYPE.INFO, moduleName, s"The chaininfo is same as most of nodes", selfAddr)
    pe.setIsSync(false)
    pe.resetSystemCurrentChainStatus(new BlockChainStatus(src.currentBlockHash.toStringUtf8(),src.currentWorldStateHash.toStringUtf8(),src.height))
    //pe.setCurrentBlockHash(src.currentBlockHash.toStringUtf8)
    clearCache()
    schedulerLink = clearSched()
    getActorRef(ActorType.BLOCK_MODULE) ! SyncResult(true, isStart, false,
      src.currentBlockHash.toStringUtf8)
  }
  
  override def receive: Receive = {
    case SetupSync =>
      //系统启动，开始同步      
      logMsg(LOG_TYPE.INFO, moduleName, "Start sync setup", selfAddr)
      clearCache
      val info = dataaccess.getBlockChainInfo()
      //pe.setCacheHeight(info.height)
      pe.resetSystemCurrentChainStatus(new BlockChainStatus(info.currentBlockHash.toStringUtf8(),info.currentWorldStateHash.toStringUtf8(),info.height))
      IsFinishedChainInfo = ChainInfoStatus.START_CHAININFO
      mediator ! Publish(BlockEvent.CHAIN_INFO_SYNC, ChainInfoReq(info, selfAddr))
      //添加初始化同步定时器
      schedulerLink = scheduler.scheduleOnce(TimePolicy.getTimeoutSync seconds, self, SyncTimeOut)
      isStart = true

    case ChainInfoReq(info, actorAddr) =>
      logMsg(LOG_TYPE.INFO, moduleName, s"Get sync req from $actorAddr", selfAddr)
      val responseInfo = dataaccess.getBlockChainInfo()
      var isSame = false
      if (responseInfo.currentWorldStateHash.toStringUtf8 == info.currentWorldStateHash.toStringUtf8)
        isSame = true
      sender() ! ChainInfoRes(info, responseInfo, isSame, selfAddr, self)

    case ChainInfoRes(src, response, isSame, addr, act) =>
      pe.getIsSync() match {
        case true =>
          if(IsFinishedChainInfo == ChainInfoStatus.START_CHAININFO){
            sender() == self match {
              case true =>
                logMsg(LOG_TYPE.INFO, moduleName, s"Sync Res from itself", selfAddr)
                pe.getNodes.size match {
                  case 1 =>
                    logMsg(LOG_TYPE.INFO, moduleName, s"Just one node in cluster", selfAddr)
                    getActorRef(pe.getSysTag, ActorType.VOTER_MODULE) ! SeedNode
                    getActorRef(pe.getSysTag, ActorType.BLOCK_MODULE) ! SyncResult(true, isStart, true,
                      src.currentBlockHash.toStringUtf8)
                    //pe.setCurrentBlockHash(src.currentBlockHash.toStringUtf8)
                    pe.resetSystemCurrentChainStatus(new BlockChainStatus(src.currentBlockHash.toStringUtf8(),src.currentWorldStateHash.toStringUtf8(),src.height))
                    clearCache()
                    pe.setIsSync(false)
                    schedulerLink = clearSched()
                  case _ => //ignore
  
                }
              case false =>
                logMsg(LOG_TYPE.INFO, moduleName, s"Get a chaininfo from $addr", selfAddr)
                syncResults.contains(response.height) match {
                  case true =>
                    syncResults += response.height -> (syncResults(response.height) :+ (ChainInfoRes(src, response, isSame, addr, act)))
                  case false =>
                    syncResults += response.height -> Array(ChainInfoRes(src, response, isSame, addr, act))
                }
                
                val info1 = dataaccess.getBlockChainInfo() 
                val cheight = info1.height
                syncMaxHight = greaterThanSelfForCount(cheight)
                if(syncMaxHight > -1){
                  if(syncMaxHight == cheight){
                    if(cheight == 0){
                      if(syncResults.contains(1)){
                        IsFinishedChainInfo = ChainInfoStatus.END_CHAININFO
                        sendSyncDataReq(1)
                        logMsg(LOG_TYPE.INFO, moduleName, s"node number:${pe.getSysTag},sync data for height 1", selfAddr)
                      }
                      else{
                        //继续等待更多的chaininfo
                      }
                    }else{
                      //可以终止同步
                      IsFinishedChainInfo = ChainInfoStatus.END_CHAININFO
                      syncFinishHandle(src)
                      logMsg(LOG_TYPE.INFO, moduleName, s"node number:${pe.getSysTag},sync is finished,local same", selfAddr)
                    }
                  }else if(syncMaxHight > cheight){
                    IsFinishedChainInfo = ChainInfoStatus.END_CHAININFO
                    sendSyncDataReq(syncMaxHight)
                    logMsg(LOG_TYPE.INFO, moduleName, s"node number:${pe.getSysTag},sync data,localheight=${cheight},max=${syncMaxHight}", selfAddr)
                  }else{
                    //小于本地高度，可以终止同步
                    IsFinishedChainInfo = ChainInfoStatus.END_CHAININFO
                    syncFinishHandle(src)
                    logMsg(LOG_TYPE.INFO, moduleName, s"node number:${pe.getSysTag},sync is finished,local big", selfAddr)
                  }
                }else{
                  //继续等待更多的chaininfo
                }
            }
          }
        case false =>
          logMsg(LOG_TYPE.INFO, moduleName, s"Sync is over", selfAddr)
      }

    case ChainDataReq(rec, infoH, blkType) =>
      logMsg(LOG_TYPE.INFO, moduleName, s"node number:${pe.getSysTag},start block number:${infoH+1},Get a data request from  $sender", selfAddr)
      // TODO 广播同步 --test
      println("#############")
      println(sender.path.toString());
      println(selfAddr);
      println("############")
      sendEvent(EventType.PUBLISH_INFO, mediator,sender.path.toString(), selfAddr,  Event.Action.BLOCK_SYNC)
      sendEventSync(EventType.PUBLISH_INFO, mediator,sender.path.toString(), selfAddr,  Event.Action.BLOCK_SYNC)
//      sendEvent(EventType.PUBLISH_INFO, mediator, selfAddr, Topic.Event, Event.Action.BLOCK_SYNC)
      val local = dataaccess.getBlockChainInfo()
      var data = Array[Block]()
      if (local.height > infoH) {
        data = dataaccess.getBlocks4ObjectFromHeight((infoH + 1).toInt)
      }
      logMsg(LOG_TYPE.INFO, moduleName, s"node number:${pe.getSysTag},data lenght:${data.size},Get a data request from  $sender", selfAddr)
      if (data != null && data.size > 0) {
        blkType match {
          case BlockSrc.SYNC_START_BLOCK =>
            var height = infoH + 1
            data.foreach(blk => {
              logMsg(LOG_TYPE.INFO, moduleName, s"node number:${pe.getSysTag},loop send data,height:${height},send to  $rec", selfAddr)
              rec ! ChainDataResSingleBlk(blk, height)
              height += 1
            })
            rec ! PersistenceModule.LastBlock(BlockHelper.getBlkHash(data(data.length - 1)), local.height,
              BlockSrc.SYNC_START_BLOCK, self)
          case BlockSrc.CONFIRMED_BLOCK =>
            //ignore
            rec ! ChainDataResSingleBlk(data(0), infoH + 1)
        }
        logMsg(LOG_TYPE.INFO, moduleName, s"node number:${pe.getSysTag},ChainDataReq,Sync data send successfully", selfAddr)
      }

    case chainDataReqSB: ChainDataReqSingleBlk =>
      logMsg(LOG_TYPE.INFO, moduleName, s"Get a data request from  $sender", selfAddr)
      val local = dataaccess.getBlockChainInfo()
      var data: Block = null
      if (local.height > chainDataReqSB.targetHeight) {
        data = dataaccess.getBlock4ObjectByHeight(chainDataReqSB.targetHeight.toInt)
      }
      if (data != null) {
        chainDataReqSB.receiver ! ChainDataResSingleBlk(data, chainDataReqSB.targetHeight)
      }
      logMsg(LOG_TYPE.INFO, moduleName, s"node number:${pe.getSysTag},ChainDataReqSingleBlk,Sync data send successfully,send to ${akka.serialization.Serialization.serializedActorPath(chainDataReqSB.receiver).toString()}", selfAddr)

    case ChainDataResSingleBlk(data, targetHeight) =>
      //收到同步区块数据
      println("***********")
      println(sender.path.toString());
      println(selfAddr);
      println(mediator);
      println("***********")
      // TODO 收到同步 --test
      sendEvent(EventType.RECEIVE_INFO, mediator, sender.path.toString(), selfAddr, Event.Action.BLOCK_SYNC)
      sendEventSync(EventType.RECEIVE_INFO, mediator,sender.path.toString(), selfAddr,  Event.Action.BLOCK_SYNC)  
      logMsg(LOG_TYPE.INFO, moduleName, s"Get a data from $sender", selfAddr)
      //TODO kami 验证信息和数据合法性，现阶段不考虑;验证创世块的合法性
      logMsg(LOG_TYPE.INFO, moduleName, s"node number:${pe.getSysTag},get single block data,height:${targetHeight},Get a data request from  $sender", selfAddr)
      getActorRef(ActorType.PERSISTENCE_MODULE) ! BlockRestore(data,
        targetHeight, BlockSrc.SYNC_START_BLOCK, sender())
      //pe.addCacheBlkNum()
      //pe.addCacheHeight()

    case LastBlock(blkHash, height, blockSrc, blker) =>
      getActorRef(ActorType.BLOCK_MODULE) ! SyncResult(true, isStart, false, blkHash)
      if (isStart) clearCache()
      schedulerLink = clearSched()
      pe.setIsSync(false)

    case SyncTimeOut =>
      pe.getIsSync() match {
        case true =>
          logMsg(LOG_TYPE.INFO, moduleName, s"Sync timeout checked, failed", selfAddr)
          //重新请求一遍
          if(syncMaxHight <= 0 ){
            self !  SetupSync
          }else{
            if(dataaccess.getBlockHeight() == syncMaxHight){
              getActorRef(ActorType.BLOCK_MODULE) ! SyncResult(true, isStart, false,
                dataaccess.getBlockChainInfo().currentBlockHash.toStringUtf8)
            }else if(dataaccess.getBlockHeight() < syncMaxHight){
                val re = syncResults(syncMaxHight)
                re.head.addActor ! ChainDataReq(self, dataaccess.getBlockChainInfo().height,
                  BlockSrc.SYNC_START_BLOCK)
                  schedulerLink = scheduler.scheduleOnce(TimePolicy.getTimeoutSync seconds, self, SyncTimeOut)
            }else{
                logMsg(LOG_TYPE.INFO, moduleName, s"System is not stable with useful nodes of chain data", selfAddr)
                if (isStart) clearCache()
                getActorRef(ActorType.BLOCK_MODULE) ! SyncResult(true, isStart, false,
                dataaccess.getBlockChainInfo().currentBlockHash.toStringUtf8)
            }
          }
        case false =>
          logMsg(LOG_TYPE.INFO, moduleName, s"Sync finished", selfAddr)

      }

    case _ => //ignore
  }

}
