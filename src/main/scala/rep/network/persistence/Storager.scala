package rep.network.persistence

import akka.actor.{ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import com.google.protobuf.ByteString
import rep.network.base.ModuleBase
import rep.network.module.ModuleManager.TargetBlock
import rep.network.Topic
import rep.protos.peer._
import rep.storage.ImpDataAccess
import rep.network.consensus.vote.CRFDVoterModule.NextVote
import scala.collection.mutable
import rep.utils.GlobalUtils.{ActorType, BlockEvent, EventType,BlockChainStatus}
import rep.network.sync.SyncModule.{ChainDataReqSingleBlk}
import scala.collection.immutable
import rep.network.cluster.ClusterHelper
import rep.log.trace.LogType


object Storager {
  def props(name: String): Props = Props(classOf[ PersistenceModule ], name)

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
  import rep.network.persistence.Storager.{BlockRestore,SourceOfBlock}
  val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)

  private var precache : immutable.TreeMap[Long,BlockRestore] = new immutable.TreeMap[Long,BlockRestore]()
  
  private def SaveBlock(blkRestore: BlockRestore):Integer={
    var re : Integer = 0
    try {
          logMsg(LogType.INFO, moduleName+"~"+ s"Merk(Before presistence): ${pe.getMerk}"+"~"+selfAddr)
          dataaccess.restoreBlock(blkRestore.blk)
          logMsg(LogType.INFO, moduleName+"~"+ s"Restore blocks success,node number: ${pe.getSysTag},block number=${blkRestore.blk.height}"+"~"+selfAddr)
          
          //ClearPreCache
          
          if(blkRestore.SourceOfBlock == SourceOfBlock.CONFIRMED_BLOCK){
            if(ClusterHelper.checkBlocker(selfAddr.toString(),akka.serialization.Serialization.serializedActorPath(blkRestore.blker).toString())){
                mediator ! Publish(Topic.Event, new Event(selfAddr, Topic.Block, Event.Action.BLOCK_NEW, Some(blkRestore.blk)))
            }
          }
          cleanTransactionInTransPool(blkRestore)
          logTime("create block storeblock time", System.currentTimeMillis(),false)
          logMsg(LogType.INFO, moduleName+"~"+ s"Merk(After presistence): ${pe.getMerk}"+"~"+selfAddr)
          logMsg(LogType.INFO, moduleName+"~"+ s"save block success,height=${pe.getCacheHeight()},hash=${pe.getCurrentBlockHash}"+"~"+selfAddr)
        }
        catch {
          case e: Exception =>
            re = 1
            logMsg(LogType.INFO, moduleName+"~"+ s"Restore blocks error : ${e.toString}"+"~"+selfAddr)
          //TODO kami 将来需要处理restore失败的情况
          case _ => //ignore
            re = 2
        }
    re
  }
  
  def cleanTransactionInTransPool(blkRestore: BlockRestore)={
    pe.removeTrans(blkRestore.blk.transactions)
    pe.resetSystemCurrentChainStatus(new BlockChainStatus(dataaccess.getBlockChainInfo().currentBlockHash.toStringUtf8,
        dataaccess.GetComputeMerkle4String,
        dataaccess.getBlockHeight()))
  }
  
  /*def CheckSync={
    this.clearSched()
    if(!precache.isEmpty){
      val max = precache.lastKey
      var syncmax = pe.getCacheHeight()
      if(!syncprecache.isEmpty){
        val tempheight = syncprecache.lastKey
        if(tempheight > syncmax){
          syncmax = tempheight
        }
      }
      val count = (max - syncmax).asInstanceOf[Int]
      if(count > 1){
        val tempblk = precache(max)
        for(i<-1 to count-1){
          context.parent ! TargetBlock(syncmax+i, tempblk.blker)
          syncprecache += (syncmax+i).asInstanceOf[Long] -> System.currentTimeMillis()
        }
      }
    }
    
    CheckSyncResendRequest
    
    ClearPreCache
    
    if(!this.precache.isEmpty){
      scheduler.scheduleOnce(20 seconds, self, PersistenceModule.checkBigBlock)
    }
  }
  
  def getSyncAddressWithBlock(BlockerAddr:String):String={
    var  addr:String = BlockerAddr
    if(BlockerAddr.indexOf("/user")>0){
      addr = BlockerAddr.substring(0, BlockerAddr.indexOf("/user")) + "/user/moduleManager/sync"
    }
    addr
  }
  
  def CheckSyncResendRequest={
    if(!precache.isEmpty){
      if(!syncprecache.isEmpty){
        val first =  syncprecache.firstKey
        var last = syncprecache.lastKey
        val tempblk = precache(precache.lastKey)
        val lcount = (last - first).asInstanceOf[Int]
        for(i<-0 to lcount){
          val oldtime = syncprecache(first+i)
          if((System.currentTimeMillis()-oldtime)/1000 > 15){
            val blockeraddr = getSyncAddressWithBlock(akka.serialization.Serialization.serializedActorPath(tempblk.blker).toString())
            val blockersyncActor = context.actorSelection(blockeraddr)
            blockersyncActor ! ChainDataReqSingleBlk(getActorRef(pe.getSysTag, ActorType.SYNC_MODULE),first+i)
            //context.parent ! TargetBlock(first+i, blockersyncActor)
            syncprecache += (first+i).asInstanceOf[Long] -> System.currentTimeMillis()
            logMsg(LogType.INFO, moduleName+"~"+ s"node number:${pe.getSysTag},send single block data req,blocker= ${akka.serialization.Serialization.serializedActorPath(tempblk.blker).toString()}"+"~"+selfAddr)
          }
        }
      }
    }else{
      syncprecache = syncprecache.empty
    }
  }
  
  
  def ClearPreCache={
    val localheight = pe.getCacheHeight()
    if(!precache.isEmpty){
      val first = precache.firstKey
      if(localheight > first){
        val lcount = (localheight - first).asInstanceOf[Int]
        for(i <- 0 to lcount){
          if(precache.contains(first+i)){
            precache -= first+i
          }
        }
      }
    }
    
    if(!syncprecache.isEmpty){
      val first = syncprecache.firstKey
      if(localheight > first){
        val lcount = (localheight - first).asInstanceOf[Int]
        for(i <- 0 to lcount){
          if(syncprecache.contains(first+i)){
            syncprecache -= first+i
          }
        }
      }
    }
  }
  */
  
  override def receive = {
    case blkRestore: BlockRestore =>
      /*logMsg(LogType.INFO, moduleName+"~"+ s"node number:${pe.getSysTag},restore single block,height:${blkRestore.height}"+"~"+selfAddr)
      var bb = blkRestore
      val re = RestoreBlock(bb)
      if(!precache.isEmpty){
        if(precache.contains(bb.height+1)){
          bb = precache(bb.height+1) 
        }else{
          bb = null
        }
        
        while(bb  != null){
          val lp = RestoreBlock(bb)
          if(lp == 1){
            if(!precache.isEmpty){
              if(precache.contains(bb.height+1)){
                bb = precache(bb.height+1) 
              }else{
                bb = null
              }
            }
          }else{
            bb = null
          }
        }
      }
      if(re == 1){
        NoticeVoteModule()
      }*/
    case checkBigBlock =>
      //CheckSync
    case _ => //ignore
  }
  
  /*def RestoreBlock(blkRestore: BlockRestore):Integer={
    var re : Integer = 0
    val local = dataaccess.getBlockChainInfo()
    if(local.currentBlockHash != ByteString.EMPTY){
        if(pe.getCurrentBlockHash.equalsIgnoreCase("0")){
          pe.resetSystemCurrentChainStatus(new BlockChainStatus(dataaccess.getBlockChainInfo().currentBlockHash.toStringUtf8,
                                          dataaccess.GetComputeMerkle4String,
                                          dataaccess.getBlockHeight()))
        }else{
          if(pe.getCurrentBlockHash != local.currentBlockHash.toStringUtf8()){
            pe.resetSystemCurrentChainStatus(new BlockChainStatus(dataaccess.getBlockChainInfo().currentBlockHash.toStringUtf8,
                                          dataaccess.GetComputeMerkle4String,
                                          dataaccess.getBlockHeight()))
          }
        }
    }
    if (blkRestore.blk.previousBlockHash.toStringUtf8 == pe.getCurrentBlockHash ||
        (local.height == 0 && blkRestore.blk.previousBlockHash == ByteString.EMPTY)) {
      logMsg(LogType.INFO, moduleName+"~"+ s"node number:${pe.getSysTag},entry save,height:${blkRestore.height}"+"~"+selfAddr)
      if(SaveBlock(blkRestore) == 0){
        //success
        re = 1
        //NoticeVoteModule()
      }else{
        println("block restor is failed in persistence module,must restart node")
        throw new Exception("block restore is failed")
      }
    }else{
      logMsg(LogType.INFO, moduleName+"~"+ s"node number:${pe.getSysTag},entry error procedure,height:${blkRestore.height},local height:${local.height}"+"~"+selfAddr)
      if(blkRestore.height <= local.height){
        RefreshCacheBuffer(blkRestore)
        logMsg(LogType.INFO, moduleName+"~"+ s"Block has already been stored"+"~"+selfAddr)
        re = 1
        //NoticeVoteModule()
      }else{
        precache += blkRestore.height -> blkRestore
        logMsg(LogType.INFO, moduleName+"~"+ s"node number:${pe.getSysTag},add block to precache,height= ${blkRestore.height}"+"~"+selfAddr)
        re = 1
        CheckSync
      }
    }
    re
  }
  
  def NoticeVoteModule()={
    logMsg(LogType.INFO, moduleName+"~"+ s"Merk(After presistence): ${pe.getMerk}"+"~"+selfAddr)
    if (pe.getCacheBlkNum() == 0){
      logMsg(LogType.INFO, moduleName+"~"+ s"presistence is over"+"~"+selfAddr)
      if(!pe.getIsSync()){
        logMsg(LogType.INFO, moduleName+"~"+ s"presistence is over,this is startup vote"+"~"+selfAddr)
        pe.setIsBlocking(false)
        pe.setEndorState(false)
        pe.setIsBlockVote(false)
        getActorRef(pe.getSysTag, ActorType.VOTER_MODULE) ! NextVote(true,0,false)
      }
    }
  }*/

 
  
  
}