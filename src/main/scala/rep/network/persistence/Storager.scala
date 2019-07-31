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

package rep.network.persistence


import akka.actor.{ ActorRef, Props }
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import com.google.protobuf.ByteString
import rep.network.base.ModuleBase
import rep.network.Topic
import rep.protos.peer._
import rep.storage.ImpDataAccess
import rep.network.consensus.vote.Voter.{VoteOfBlocker}
import scala.collection.mutable
import rep.utils.GlobalUtils.{ ActorType, BlockEvent, EventType, NodeStatus }
import scala.collection.immutable
import rep.network.sync.SyncMsg.{SyncRequestOfStorager,StartSync}
import scala.util.control.Breaks._
import rep.network.util.NodeHelp
import rep.network.sync.SyncMsg.{BlockDataOfRequest,BlockDataOfResponse}
import rep.log.RepLogger
import rep.log.RepTimeTracer


object Storager {
  def props(name: String): Props = Props(classOf[Storager], name)

  final case class BlockRestore(blk: Block, SourceOfBlock: Int, blker: ActorRef)
  final case object BatchStore

  case object SourceOfBlock {
    val CONFIRMED_BLOCK = 1
    val SYNC_BLOCK = 2
    val TEST_PROBE = 3
  }

  def SourceOfBlockToString(s: Int): String = {
    s match {
      case 1 => "CONFIRMED_BLOCK"
      case 2 => "SYNC_BLOCK"
    }
  }

}

class Storager(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.concurrent.duration._
  import rep.network.persistence.Storager.{ BlockRestore, SourceOfBlock ,BatchStore}

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Storager_Logger, this.getLogMsgPrefix( "Storager Start"))
  }
  
  val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)

  //private var precache: immutable.TreeMap[Long, BlockRestore] = new immutable.TreeMap[Long, BlockRestore]()

  private def SaveBlock(blkRestore: BlockRestore): Integer = {
    var re: Integer = 0
    try {
     RepTimeTracer.setStartTime(pe.getSysTag, "storage-save", System.currentTimeMillis(),blkRestore.blk.height,blkRestore.blk.transactions.size)
      RepLogger.trace(RepLogger.Storager_Logger, this.getLogMsgPrefix( s"PreBlockHash(Before presistence): ${pe.getCurrentBlockHash}" + "~" + selfAddr))
      val result = dataaccess.restoreBlock(blkRestore.blk)
      if (result._1) {
        RepLogger.trace(RepLogger.Storager_Logger, this.getLogMsgPrefix( s"Restore blocks success,node number: ${pe.getSysTag},block number=${blkRestore.blk.height}" + "~" + selfAddr))
        
        RepTimeTracer.setEndTime(pe.getSysTag, "storage-save", System.currentTimeMillis(),blkRestore.blk.height,blkRestore.blk.transactions.size)
        
        if(blkRestore.SourceOfBlock == SourceOfBlock.CONFIRMED_BLOCK && pe.getSysTag == pe.getBlocker.blocker && pe.getBlocker.VoteHeight+1 == blkRestore.blk.height){
          RepTimeTracer.setEndTime(pe.getSysTag, "Block", System.currentTimeMillis(),blkRestore.blk.height,blkRestore.blk.transactions.size)
        }
        
        pe.getTransPoolMgr.removeTrans(blkRestore.blk.transactions,pe.getSysTag)
        pe.resetSystemCurrentChainStatus(new BlockchainInfo(result._2, result._3, ByteString.copyFromUtf8(result._4), ByteString.copyFromUtf8(result._5),ByteString.copyFromUtf8(result._6)))
        pe.getBlockCacheMgr.removeFromCache(blkRestore.blk.height)
        
        if (blkRestore.SourceOfBlock == SourceOfBlock.CONFIRMED_BLOCK && NodeHelp.checkBlocker(selfAddr, akka.serialization.Serialization.serializedActorPath(blkRestore.blker))) {
            mediator ! Publish(Topic.Event, new Event(selfAddr, Topic.Block, Event.Action.BLOCK_NEW, Some(blkRestore.blk)))
        }
        
        RepLogger.trace(RepLogger.Storager_Logger, this.getLogMsgPrefix( s"CurrentHash(After presistence): ${pe.getCurrentBlockHash}" + "~" + selfAddr))
        RepLogger.trace(RepLogger.Storager_Logger, this.getLogMsgPrefix( s"save block success,height=${pe.getCurrentHeight},hash=${pe.getCurrentBlockHash}" + "~" + selfAddr))
      } else {
        throw new Exception(s"Restore blocks error,save block info:height=${blkRestore.blk.height},prehash=${blkRestore.blk.previousBlockHash.toStringUtf8()},currenthash=${blkRestore.blk.hashOfBlock.toStringUtf8()}")
      }

    } catch {
      case e: Exception =>
        re = 1
        RepLogger.trace(RepLogger.Storager_Logger, this.getLogMsgPrefix( s"Restore blocks error : ${e.getMessage}" + "~" + selfAddr))
    }
    re
  }

  private def NoticeVoteModule = {
    if (pe.getBlockCacheMgr.isEmpty ) {
      RepLogger.trace(RepLogger.Storager_Logger, this.getLogMsgPrefix("presistence is over,this is startup vote" + "~" + selfAddr))
      //通知抽签模块，开始抽签
      pe.getActorRef( ActorType.voter) ! VoteOfBlocker
    }else{
      RepLogger.trace(RepLogger.Storager_Logger, this.getLogMsgPrefix( s"presistence is over,cache has data,do not vote,height=${pe.getCurrentHeight} ~" + selfAddr))
    }
  }
  
  private def NoticeSyncModule(blker: ActorRef) = {
    RepLogger.trace(RepLogger.Storager_Logger, this.getLogMsgPrefix( s"presistence is failed,must start sync ,height=${pe.getCurrentHeight} ~" + selfAddr))
    if(!pe.getBlockCacheMgr.isEmpty){
      RepLogger.trace(RepLogger.Storager_Logger, this.getLogMsgPrefix( s"presistence is failed,must start sync,start send sync request ,height=${pe.getCurrentHeight} ~" + selfAddr))
      val hs = pe.getBlockCacheMgr.getKeyArray4Sort
      val max = hs(hs.length-1)
      pe.getActorRef(ActorType.synchrequester) ! SyncRequestOfStorager(blker,max)
    }
  }

  private def Handler={
    try {
      var localchaininfo = pe.getSystemCurrentChainStatus
      if (localchaininfo.height <= 0) {
        localchaininfo = dataaccess.getBlockChainInfo()
        pe.resetSystemCurrentChainStatus(localchaininfo)
      }
      val hs = pe.getBlockCacheMgr.getKeyArray4Sort
      val minheight = hs(0)
      val maxheight = hs(hs.length-1)
      var loop :Long = minheight
      
      breakable(
          while(loop <= maxheight){
            val _blkRestore = pe.getBlockCacheMgr.getBlockFromCache(loop)
            if(loop > localchaininfo.height+1){
              //发送同步消息
              if(!pe.isSynching){
                NoticeSyncModule(_blkRestore.blker)
              }
              break
            }else{
              val r = RestoreBlock(_blkRestore)
              if(r == 0){
                localchaininfo = pe.getSystemCurrentChainStatus
              }
            }
            loop += 1l
          }
      )
     NoticeVoteModule
    }catch{
      case e: RuntimeException =>
        RepLogger.trace(RepLogger.Storager_Logger, this.getLogMsgPrefix( s"Restore blocks error : ${e.getMessage}" + "~" + selfAddr))
    }
  }
  
  def RestoreBlock(blkRestore: BlockRestore): Integer = {
    var re: Integer = 1
    try {
      if (blkRestore.blk.height == (pe.getCurrentHeight + 1)) {
        if (blkRestore.blk.previousBlockHash.toStringUtf8 == pe.getCurrentBlockHash ||
          (pe.getCurrentHeight == 0 && blkRestore.blk.previousBlockHash == ByteString.EMPTY)) {
          RepLogger.trace(RepLogger.Storager_Logger, this.getLogMsgPrefix(s"node number:${pe.getSysTag},entry save,height:${blkRestore.blk.height}" + "~" + selfAddr))
          if (SaveBlock(blkRestore) == 0) {
            if(blkRestore.SourceOfBlock == SourceOfBlock.TEST_PROBE){
              sender ! 0
            }
            re = 0
          } else {
            pe.getBlockCacheMgr.removeFromCache(blkRestore.blk.height)
            RepLogger.trace(RepLogger.Storager_Logger, this.getLogMsgPrefix(s"block save is failed in persistence module,must restart height:${blkRestore.blk.height}" + "~" + selfAddr))
          }
        } else {
          pe.getBlockCacheMgr.removeFromCache(blkRestore.blk.height)
          pe.getActorRef(ActorType.synchrequester) ! StartSync(false)
          RepLogger.trace(RepLogger.Storager_Logger, this.getLogMsgPrefix(s"block restor is failed in persistence module,block prehash  error,must restart height:${blkRestore.blk.height}" + "~" + selfAddr))
        }
      } else if (blkRestore.blk.height < (pe.getCurrentHeight + 1)) {
         pe.getBlockCacheMgr.removeFromCache(blkRestore.blk.height)
          RepLogger.trace(RepLogger.Storager_Logger, this.getLogMsgPrefix(s"Restore blocks error : current height=${blkRestore.blk.height} less than local height${pe.getCurrentHeight}" + "~" + selfAddr))
      }
    } catch {
      case e: RuntimeException =>
        RepLogger.trace(RepLogger.Storager_Logger, this.getLogMsgPrefix( s"Restore blocks error : ${e.getMessage}" + "~" + selfAddr))
    }
    re
  }
  
  override def receive = {
    case blkRestore: BlockRestore =>
      RepLogger.trace(RepLogger.Storager_Logger, this.getLogMsgPrefix( s"node number:${pe.getSysTag},restore single block,height:${blkRestore.blk.height}" + "~" + selfAddr))
      RepTimeTracer.setStartTime(pe.getSysTag, "storage-handle", System.currentTimeMillis(),blkRestore.blk.height,blkRestore.blk.transactions.size)
      pe.getBlockCacheMgr.addToCache(blkRestore)
      Handler
      RepTimeTracer.setEndTime(pe.getSysTag, "storage-handle", System.currentTimeMillis(),blkRestore.blk.height,blkRestore.blk.transactions.size)
      
    case  BatchStore =>
       RepTimeTracer.setStartTime(pe.getSysTag, "storage-handle-noarg", System.currentTimeMillis(),pe.getCurrentHeight,120)
        Handler
        RepTimeTracer.setEndTime(pe.getSysTag, "storage-handle-noarg", System.currentTimeMillis(),pe.getCurrentHeight,120) 
    case _             => //ignore
  }

}