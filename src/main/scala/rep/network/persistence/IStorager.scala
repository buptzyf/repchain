package rep.network.persistence

import akka.actor.{ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import com.google.protobuf.ByteString
import rep.app.conf.SystemCertList
import rep.log.{RepLogger, RepTimeTracer}
import rep.network.autotransaction.Topic
import rep.network.base.ModuleBase
import rep.network.module.cfrd.CFRDActorType
import rep.network.sync.SyncMsg.{StartSync, SyncRequestOfStorager}
import rep.network.util.NodeHelp
import rep.protos.peer.{BlockchainInfo, Event}
import rep.storage.ImpDataAccess
import rep.network.consensus.common.MsgOfConsensus.{BatchStore,BlockRestore}
import scala.util.control.Breaks.{break, breakable}

/**
 * Created by jiangbuyun on 2020/03/19.
 * 实现区块存储的抽象的actor
 */


object IStorager{
  def props(name: String): Props = Props(classOf[IStorager], name)

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

abstract class IStorager (moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.concurrent.duration._
  import rep.network.persistence.IStorager.{ SourceOfBlock }

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

  protected def sendVoteMessage:Unit

  private def NoticeVoteModule = {
    if (NodeHelp.isCandidateNow(pe.getSysTag, SystemCertList.getSystemCertList)) {
      if (pe.getBlockCacheMgr.isEmpty ) {
        RepLogger.trace(RepLogger.Storager_Logger, this.getLogMsgPrefix("presistence is over,this is startup vote" + "~" + selfAddr))
        //通知抽签模块，开始抽签
        this.sendVoteMessage
      }else{
        RepLogger.trace(RepLogger.Storager_Logger, this.getLogMsgPrefix( s"presistence is over,cache has data,do not vote,height=${pe.getCurrentHeight} ~" + selfAddr))
      }
    }
  }

  private def NoticeSyncModule(blker: ActorRef) = {
    RepLogger.trace(RepLogger.Storager_Logger, this.getLogMsgPrefix( s"presistence is failed,must start sync ,height=${pe.getCurrentHeight} ~" + selfAddr))
    if(!pe.getBlockCacheMgr.isEmpty){
      RepLogger.trace(RepLogger.Storager_Logger, this.getLogMsgPrefix( s"presistence is failed,must start sync,start send sync request ,height=${pe.getCurrentHeight} ~" + selfAddr))
      val hs = pe.getBlockCacheMgr.getKeyArray4Sort
      val max = hs(hs.length-1)
      pe.getActorRef(CFRDActorType.ActorType.synchrequester) ! SyncRequestOfStorager(blker,max)
    }
  }

  private def Handler={
    try {
      var localchaininfo = pe.getSystemCurrentChainStatus
      if (localchaininfo.height <= 0) {
        localchaininfo = dataaccess.getBlockChainInfo()
        pe.resetSystemCurrentChainStatus(localchaininfo)
      }
      if(!pe.getBlockCacheMgr.isEmpty){
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
      }
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
          pe.getActorRef(CFRDActorType.ActorType.synchrequester) ! StartSync(false)
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
      RepTimeTracer.setStartTime(pe.getSysTag, "storage-handle-noarg-batch", System.currentTimeMillis(),pe.getCurrentHeight,120)
      Handler
      RepTimeTracer.setEndTime(pe.getSysTag, "storage-handle-noarg-batch", System.currentTimeMillis(),pe.getCurrentHeight,120)
    case _             => //ignore
  }
}
