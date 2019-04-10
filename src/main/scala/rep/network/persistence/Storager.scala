package rep.network.persistence

import akka.actor.{ ActorRef, Props }
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import com.google.protobuf.ByteString
import rep.network.base.ModuleBase
import rep.network.module.ModuleManager.TargetBlock
import rep.network.Topic
import rep.protos.peer._
import rep.storage.ImpDataAccess
import rep.network.consensus.vote.Voter.{VoteOfBlocker}
import scala.collection.mutable
import rep.utils.GlobalUtils.{ ActorType, BlockEvent, EventType, NodeStatus }
import scala.collection.immutable
import rep.network.cluster.ClusterHelper
import rep.log.trace.LogType
import rep.network.sync.SyncMsg
import scala.util.control.Breaks._


object Storager {
  def props(name: String): Props = Props(classOf[Storager], name)

  final case class BlockRestore(blk: Block, SourceOfBlock: Int, blker: ActorRef)

  case object SourceOfBlock {
    val CONFIRMED_BLOCK = 1
    val SYNC_BLOCK = 2
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
  import rep.network.persistence.Storager.{ BlockRestore, SourceOfBlock }
  val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)

  //private var precache: immutable.TreeMap[Long, BlockRestore] = new immutable.TreeMap[Long, BlockRestore]()

  private def SaveBlock(blkRestore: BlockRestore): Integer = {
    var re: Integer = 0
    try {
      logMsg(LogType.INFO, moduleName + "~" + s"PreBlockHash(Before presistence): ${pe.getCurrentBlockHash}" + "~" + selfAddr)
      val result = dataaccess.restoreBlock(blkRestore.blk)
      if (result._1) {
        logMsg(LogType.INFO, moduleName + "~" + s"Restore blocks success,node number: ${pe.getSysTag},block number=${blkRestore.blk.height}" + "~" + selfAddr)
        pe.getTransPoolMgr.removeTrans(blkRestore.blk.transactions)
        pe.resetSystemCurrentChainStatus(new BlockchainInfo(result._2, result._3, ByteString.copyFromUtf8(result._4), ByteString.copyFromUtf8(result._5)))
        pe.getBlockCacheMgr.removeFromCache(blkRestore.blk.height)
        
        if (blkRestore.SourceOfBlock == SourceOfBlock.CONFIRMED_BLOCK) {
          if (ClusterHelper.checkBlocker(selfAddr.toString(), akka.serialization.Serialization.serializedActorPath(blkRestore.blker).toString())) {
            mediator ! Publish(Topic.Event, new Event(selfAddr, Topic.Block, Event.Action.BLOCK_NEW, Some(blkRestore.blk)))
          }
        }

        logTime("create block storeblock time", System.currentTimeMillis(), false)
        logMsg(LogType.INFO, moduleName + "~" + s"CurrentHash(After presistence): ${pe.getCurrentBlockHash}" + "~" + selfAddr)
        logMsg(LogType.INFO, moduleName + "~" + s"save block success,height=${pe.getCurrentHeight},hash=${pe.getCurrentBlockHash}" + "~" + selfAddr)
      } else {
        throw new Exception(s"Restore blocks error,save block info:height=${blkRestore.blk.height},prehash=${blkRestore.blk.previousBlockHash.toStringUtf8()},currenthash=${blkRestore.blk.hashOfBlock.toStringUtf8()}")
      }

    } catch {
      case e: Exception =>
        re = 1
        logMsg(LogType.INFO, moduleName + "~" + s"Restore blocks error : ${e.getMessage}" + "~" + selfAddr)
    }
    re
  }

  private def NoticeVoteModule = {
    if (pe.getBlockCacheMgr.isEmpty && pe.getSystemStatus == NodeStatus.Ready) {
      logMsg(LogType.INFO, moduleName + "~" + s"presistence is over,this is startup vote" + "~" + selfAddr)
      //通知抽签模块，开始抽签
      getActorRef(pe.getSysTag, ActorType.VOTER_MODULE) ! VoteOfBlocker
    }else{
      logMsg(LogType.INFO, moduleName + "~" + s"presistence is over,cache has data,do not vote,height=${pe.getCurrentHeight} ~" + selfAddr)
    }
  }
  
  private def NoticeSyncModule(blker: ActorRef) = {
    logMsg(LogType.INFO, moduleName + "~" + s"presistence is failed,must start sync ,height=${pe.getCurrentHeight} ~" + selfAddr)
    if(!pe.getBlockCacheMgr.isEmpty){
      logMsg(LogType.INFO, moduleName + "~" + s"presistence is failed,must start sync,start send sync request ,height=${pe.getCurrentHeight} ~" + selfAddr)
      val hs = pe.getBlockCacheMgr.getKeyArray4Sort
      val max = hs(hs.length-1)
      val min = pe.getCurrentHeight+1
      pe.setSystemStatus(NodeStatus.Synching) 
      pe.getActorRef(ActorType.SYNC_MODULE) ! SyncMsg.SyncRequestOfStorager(blker,min,max)
    }
  }

  private def Handler(_blkRestore: BlockRestore)={
    pe.getBlockCacheMgr.addToCache(_blkRestore)
    try {
      var localchaininfo = pe.getSystemCurrentChainStatus
      if (localchaininfo.height <= 0) {
        localchaininfo = dataaccess.getBlockChainInfo()
        pe.resetSystemCurrentChainStatus(localchaininfo)
      }
      val hs = pe.getBlockCacheMgr.getKeyArray4Sort
      val minheight = hs(0)
      val maxheight = hs(hs.length-1)
      val loop :Long = minheight
      breakable(
          while(loop <= maxheight){
            if(loop > localchaininfo.height+1){
              //发送同步消息
              NoticeSyncModule(_blkRestore.blker)
              break
            }else{
              RestoreBlock(pe.getBlockCacheMgr.getBlockFromCache(loop))
            }
          }
      )
     NoticeVoteModule
    }catch{
      case e: RuntimeException =>
        logMsg(LogType.ERROR, moduleName + "~" + s"Restore blocks error : ${e.getMessage}" + "~" + selfAddr)
    }
  }
  
  def RestoreBlock(blkRestore: BlockRestore): Integer = {
    var re: Integer = 1
    try {
      if (blkRestore.blk.height == (pe.getCurrentHeight + 1)) {
        if (blkRestore.blk.previousBlockHash.toStringUtf8 == pe.getCurrentBlockHash ||
          (pe.getCurrentHeight == 0 && blkRestore.blk.previousBlockHash == ByteString.EMPTY)) {
          logMsg(LogType.INFO, moduleName + "~" + s"node number:${pe.getSysTag},entry save,height:${blkRestore.blk.height}" + "~" + selfAddr)
          if (SaveBlock(blkRestore) == 0) {
            re = 0
          } else {
            pe.getBlockCacheMgr.removeFromCache(blkRestore.blk.height)
            logMsg(LogType.ERROR, moduleName + "~" + s"block restor is failed in persistence module,must restart height:${blkRestore.blk.height}" + "~" + selfAddr)
          }
        } else {
          pe.getBlockCacheMgr.removeFromCache(blkRestore.blk.height)
          logMsg(LogType.ERROR, moduleName + "~" + s"block restor is failed in persistence module,must restart height:${blkRestore.blk.height}" + "~" + selfAddr)
        }
      } else if (blkRestore.blk.height < (pe.getCurrentHeight + 1)) {
         pe.getBlockCacheMgr.removeFromCache(blkRestore.blk.height)
          logMsg(LogType.INFO, moduleName + "~" + s"Restore blocks error : current height=${blkRestore.blk.height} less than local height${pe.getCurrentHeight}" + "~" + selfAddr)
      }
    } catch {
      case e: RuntimeException =>
        logMsg(LogType.ERROR, moduleName + "~" + s"Restore blocks error : ${e.getMessage}" + "~" + selfAddr)
    }
    re
  }
  
  override def receive = {
    case blkRestore: BlockRestore =>
      logMsg(LogType.INFO, moduleName + "~" + s"node number:${pe.getSysTag},restore single block,height:${blkRestore.blk.height}" + "~" + selfAddr)
      Handler(blkRestore)
    case _             => //ignore
  }

}