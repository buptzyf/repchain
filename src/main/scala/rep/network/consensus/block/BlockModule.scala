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

package rep.network.consensus.block

import akka.actor.{ActorRef, Address, Props}
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import com.google.protobuf.ByteString
import rep.app.conf.{SystemProfile, TimePolicy}
import rep.crypto.Sha256
import rep.network.consensus.vote.CRFDVoterModule.NextVote
import rep.network._
import rep.network.base.ModuleBase
import rep.network.consensus.block.BlockModule._
import rep.network.cluster.ClusterHelper
import rep.network.consensus.CRFD.CRFD_STEP
import rep.network.persistence.PersistenceModule
import rep.network.persistence.PersistenceModule.{BlockRestore, BlockSrc}
import rep.network.sync.SyncModule.SyncResult
import rep.network.consensus.transaction.PreloadTransactionModule.PreTransFromType
import rep.protos.peer._
import rep.storage.ImpDataAccess
import rep.utils.GlobalUtils.{ActorType, BlockEvent, EventType,BlockChainStatus}
import scala.collection.mutable
import com.sun.beans.decoder.FalseElementHandler
import scala.util.control.Breaks
import rep.log.trace.LogType

/**
  * 出块模块伴生对象
  *
  * @author shidianyue
  * @version 1.0
  * @update 2018-05 jiangbuyun
  **/
object BlockModule {

  def props(name: String): Props = Props(classOf[ BlockModule ], name)

  case object EndorseStatus {
    val START_ENDORSE = "start"
    val END_ENDORSE = "end"
    val READY_ENDORSE = "ready"
  }
  
  case class ReadyEndorsement4Block(blc: Block, blkidentifier:String)
  
  //打包待预执行块
  case class PreTransBlock(blc: Block, from: Int,blcIndentifier:String)

  //块预执行结果
  case class PreTransBlockResult(blc: Block, result: Boolean, merk: String, errorType: Int, error: String,blcIndentifier:String)

  //创世块预执行结果
  case class PreTransBlockResultGensis(blc: Block, result: Boolean, merk: String, errorType: Int, error: String)

  //打包块待背书
  case class PrimaryBlock(blc: Block, blocker: String,voteinedx:Int,blkidentifier:String,sendertime:Long)
  
  case class PrimaryBlock4Cache(blc: Block, blocker: String,voteinedx:Int,actRef: ActorRef,blkidentifier:String,sendertime:Long)
  
  case object RepeatCheckEndorseCache
  

  //块背书结果
  case class EndorsedBlock(isSuccess: Boolean, blc: Block, endor: Endorsement,blkidentifier:String)

  //正式块
  case class ConfirmedBlock(blc: Block, height: Long, actRef: ActorRef)

  //块链数据
  case class BlockChainData(bc: mutable.HashMap[ String, Block ])

  //出块超时
  case object CreateBlockTimeOut

  //出块请求
  case class NewBlock(blc:Block,newblocker:ActorRef,currentIdentifier:String)

  //创世块请求
  case object GenesisBlock

  //出块模块初始化完成
  case object BlockModuleInitFinished
  
  case object ResendEndorseInfo

}

/**
  * 出块模块
  *
  * @author shidianyue
  * @version 1.0
  * @since 1.0
  * @param moduleName 模块名称
  **/
class BlockModule(moduleName: String) extends ModuleBase(moduleName) {

  import context.dispatcher
  import scala.concurrent.duration._
  import akka.actor.ActorSelection 
  import scala.collection.mutable.ArrayBuffer
  import rep.protos.peer.{Transaction}

  val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)

  //缓存当前块
  var blc = new Block()
  var blkidentifier_str = ""
  
  var IsFinishedEndorse :String= EndorseStatus.READY_ENDORSE
  
  private val recvedEndorseAddr = new mutable.HashMap[ String, String ]()
  
  var schedulerLink1: akka.actor.Cancellable = null

  def scheduler1 = context.system.scheduler
  def clearSched1() = {
    if (schedulerLink1 != null) schedulerLink1.cancel()
    null
  }
  
  var isGensis = false

  /**
    * Clear the cache for new block
    */
  def clearCache(): Unit = {
    blc = new Block()
    blkidentifier_str = ""
    pe.setEndorState(false)
    pe.setIsBlocking(false)
    recvedEndorseAddr.clear()
    schedulerLink1 = clearSched1()
    IsFinishedEndorse = EndorseStatus.READY_ENDORSE
    
    //BlockTimeStatis4Times.clear
  }

  def visitService(sn : Address, actorName:String,p:PrimaryBlock) = {  
        try {  
          val  selection : ActorSelection  = context.actorSelection(toAkkaUrl(sn , actorName));  
          selection ! p 
        } catch  {  
             case e: Exception => e.printStackTrace()
        }  
    }  
  
  def addEndoserNode(endaddr:String,actorName:String)={
    if(endaddr.indexOf(actorName)>0){
      val addr = endaddr.substring(0, endaddr.indexOf(actorName))
      if(!recvedEndorseAddr.contains(addr) ){
          recvedEndorseAddr += addr -> ""
      }
    }
  }
  
  def resendEndorser(actorName:String,p:PrimaryBlock)={
    pe.getStableNodes.foreach(sn=>{
            if(!recvedEndorseAddr.contains(sn.toString)){
              //visitService(sn , "/user/moduleManager/consensusManager/consensus-CRFD/endorse",PrimaryBlock(blc, pe.getBlocker,pe.getBlker_index))
              if(this.selfAddr.indexOf(sn) == -1){
                visitService(sn , "/user/moduleManager/consensusManager/consensus-CRFD/endorse",p)
              }
            }
       })
  }
  
    def toAkkaUrl(sn : Address, actorName:String):String = {  
        return sn.toString + "/"  + actorName;  
    }  
    
    def resetVoteEnv={
        this.clearCache()
        pe.setIsBlocking(false)
        pe.setEndorState(false)
        pe.setIsBlockVote(false)
        getActorRef(pe.getSysTag, ActorType.VOTER_MODULE) ! NextVote(true,0,false)
    }
    
    def isExistEndorse(endor: Endorsement):Boolean={
      var r = false
      val es = blc.consensusMetadata
      if(es.length > 0){
        val loopbreak = new Breaks
        loopbreak.breakable(
            for(i <- 0 to (es.length-1) ){
              val h = es(i)
              if(h.endorser.toStringUtf8() == endor.endorser.toStringUtf8()){
                r = true
                loopbreak.break
              }
            }
        )
      }
      r
    }
  
    def getTransListFromTransPool(num: Int): Seq[Transaction] = {
    val result = ArrayBuffer.empty[ Transaction ]
    try{
      val tmplist = pe.getTransListClone(num)
      if(tmplist.size > 0){
        val currenttime = System.currentTimeMillis()/1000
        val sr: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
        tmplist.foreach(f=>{
          //判断交易是否超时，把超时的交易删除
          if((currenttime - f.createTime) > TimePolicy.getTranscationWaiting){
            pe.removeTranscation(f.t)
          }else{
            //判断交易是否已经被打包入块，如果已经打包入块需要删除
            if(sr.getBlockByTxId(f.t.txid) != null){
              pe.removeTranscation(f.t)
            }else{
              f.t +=: result
            }
          }
        })
      }
    }finally{
    }
    result.reverse
  }
    
  override def preStart(): Unit = {
    logMsg(LogType.INFO, "Block module start")
    //TODO kami 这里值得注意：整个订阅过s程也是一个gossip过程，并不是立即生效。需要等到gossip一致性成功之后才能够receive到注册信息。
    SubscribeTopic(mediator, self, selfAddr, Topic.Block, true)
    scheduler.scheduleOnce(TimePolicy.getStableTimeDur millis, context.parent, BlockModuleInitFinished)
  }

  def sort(src: Array[Endorsement]){  
  
    def swap(i:Integer, j:Integer){  
      val t = src(i)
      src(i) = src(j)
      src(j) = t  
    }  
  
    def sortSub(l: Int, r: Int){  
      val half = src((l + r)/2)  
      var i = l; var j = r;  
      while (i <= j){  
        while (src(i).endorser.toStringUtf8() < half.endorser.toStringUtf8()) i += 1  
        while (src(j).endorser.toStringUtf8() > half.endorser.toStringUtf8()) j -= 1  
        if(i <= j){  
          swap(i, j)  
          i += 1  
          j -= 1  
        }  
      }  
      if(l < j) sortSub(l, j)  
      if(j < r) sortSub(i, r)  
    }  
    sortSub(0, src.length - 1)  
  }  

  
  override def receive = {
    //创建块请求（给出块人）
    case (BlockEvent.CREATE_BLOCK, _) =>
      //触发原型块生成
      pe.getIsSync() match {
        case false =>
          //isBlocking match {
          pe.getIsBlocking() match {
            case false =>
              (IsFinishedEndorse == EndorseStatus.END_ENDORSE || IsFinishedEndorse == EndorseStatus.START_ENDORSE)&& blc.previousBlockHash.toStringUtf8() == pe.getCurrentBlockHash match{
                case true => pe.setIsBlocking(true)
                case false=>
                  //清空缓存
                  schedulerLink = clearSched()
                  clearCache()
                  //准备出块
                  
                  logMsg(LogType.INFO, "Create Block start in BlockEvent.CREATE_BLOCK")
                  logTime("Create Block time", System.currentTimeMillis(),true)
                  logTime("blocker，from create to finish time", System.currentTimeMillis(),true)
                  //From memberListener itself
                  //发送候选人事件
                  sendEvent(EventType.PUBLISH_INFO, mediator, selfAddr, Topic.Block, Event.Action.CANDIDATOR)
                  val translist = getTransListFromTransPool(SystemProfile.getLimitBlockTransNum)
                  //交易列表中的交易数量，当前用大于0即可，以后应该可以根据设置SystemProfile.getMinBlockTransNum来进行调整
                  if(translist.size > 0){
                    
                    logTime("create block select Trans time", System.currentTimeMillis(),true)
                    blc = BlockHelper.createPreBlock(pe.getCurrentBlockHash, translist)
                    blkidentifier_str = pe.getSysTag+"_"+System.nanoTime().toString()
                    
                    logMsg(LogType.INFO,s"Create Block end in BlockEvent.CREATE_BLOCK,current height=${pe.getCacheHeight()},current hash=${pe.getCurrentBlockHash},identifier=${blkidentifier_str}")
                    pe.setEndorState(false)
                    pe.setIsBlocking(true)
                    logMsg(LogType.INFO, s"Create Block with transcation,transaction size ${blc.transactions.size},node number=${pe.getSysTag},block identifier=${blkidentifier_str}")
                    logTime("create block select Trans time", System.currentTimeMillis(),false)
                    logTime("create block preload Trans time", System.currentTimeMillis(),true)
                    getActorRef(pe.getSysTag, ActorType.PRELOADTRANS_MODULE) ! PreTransBlock(blc, PreTransFromType.BLOCK_CREATOR,blkidentifier_str)
                    schedulerLink = scheduler.scheduleOnce(TimePolicy.getTimeOutBlock seconds, self, CreateBlockTimeOut)
                  }else{
                    logMsg(LogType.INFO, "Blocking, transaction number is not enough,waiting for next block request in BlockEvent.CREATE_BLOCK")
                    getActorRef(pe.getSysTag, ActorType.VOTER_MODULE) ! NextVote(true,0,false)
                  }
                  logTime("Create Block time", System.currentTimeMillis(),false)
              }
            case true =>
             logMsg(LogType.INFO, "Blocking, waiting for next block request in BlockEvent.CREATE_BLOCK")
              getActorRef(pe.getSysTag, ActorType.VOTER_MODULE) ! NextVote(true,0,false)
          }
      case true =>
          logMsg(LogType.INFO, "Syncing, waiting for next block request")
          getActorRef(pe.getSysTag, ActorType.VOTER_MODULE) ! NextVote(true,0,false)
      }
    //系统开始出块通知（给非出块人）
    case (BlockEvent.BLOCKER, addr) =>
      pe.getIsSync() match {
        case false =>
          //isBlocking match {
          pe.getIsBlocking() match {
            case false =>
                IsFinishedEndorse == EndorseStatus.END_ENDORSE && blc.previousBlockHash.toStringUtf8() == pe.getCurrentBlockHash match{
                case true => 
                  pe.setIsBlocking(true)
                  logMsg(LogType.INFO, s"vote notice,current is not blocker,previous is blocker,and is endorsing")
                case false=>
                  //背书获取当前出块人（更新出块状态）
                  //isBlocking = true
                  schedulerLink = clearSched()
                  clearCache()
                  pe.setIsBlocking(true)
                  IsFinishedEndorse = EndorseStatus.READY_ENDORSE
                  //添加出块定时器
                  schedulerLink = scheduler.scheduleOnce(TimePolicy.getTimeOutBlock seconds, self, CreateBlockTimeOut)
                  //如果不是出块人，发送消息通知背书模块，是否有背书缓存请求，如果有，检查缓存的请求，进行背书。
                  getActorRef(ActorType.ENDORSE_MODULE) ! RepeatCheckEndorseCache
                  logMsg(LogType.INFO, s"vote notice,current is not blocker")
              }
            case true =>
              logMsg(LogType.INFO, "Blocking, waiting for next block request in BlockEvent.BLOCKER")
              getActorRef(pe.getSysTag, ActorType.VOTER_MODULE) ! NextVote(true,0,false)
          }
        case true =>
          logMsg(LogType.INFO, "Syncing, waiting for next block request")
          getActorRef(pe.getSysTag, ActorType.VOTER_MODULE) ! NextVote(true,0,false)
      }
    //出块预执行结果
    case PreTransBlockResult(blk, result, merk, errorType, error,blkIdentifier) =>
      //println("blk.previoushash="+blk.previousBlockHash.toStringUtf8()+"\tpe.currentbhash="+pe.getCurrentBlockHash)
      if(blk.previousBlockHash.toStringUtf8() != pe.getCurrentBlockHash){
        resetVoteEnv
      }else
      result match {
        case true =>
          blkidentifier_str == blkidentifier_str match {
            case true =>
              logTime("create block preload Trans time", System.currentTimeMillis(),false)
              logMsg(LogType.INFO, s"PreTransBlockResult ... with transcation,transaction size ${blk.transactions.size},node number=${pe.getSysTag},block identifier=${blkidentifier_str}")
              blc = blk.withStateHash(ByteString.copyFromUtf8(merk))
              blc = blc.withConsensusMetadata(Seq(BlockHelper.endorseBlock(Sha256.hash(blc.toByteArray), pe.getSysTag)))
              //Broadcast the Block
              //这个块经过预执行之后已经包含了预执行结果和状态
              //mediator ! Publish(BlockEvent.BLOCK_ENDORSEMENT, PrimaryBlock(blc, pe.getBlocker))
              logMsg(LogType.INFO, s"node name=${pe.getSysTag},preload finish,self cert=${blc.consensusMetadata(0).endorser.toStringUtf8()}")
              logTime("create block endorse time", System.currentTimeMillis(),true)
              
              getActorRef(ActorType.ENDORSE_BLOCKER) ! ReadyEndorsement4Block(blc,blkidentifier_str)
              
              
              /*schedulerLink1 = scheduler1.scheduleOnce(TimePolicy.getTimeoutEndorse seconds, self, ResendEndorseInfo)
              IsFinishedEndorse = EndorseStatus.START_ENDORSE
              
              
              BlockTimeStatis4Times.setStartEndorse(System.currentTimeMillis())
              
              pe.getStableNodes.foreach(sn=>{
                visitService(sn , "/user/moduleManager/consensusManager/consensus-CRFD/endorse",PrimaryBlock(blc, pe.getBlocker,pe.getBlker_index,blkidentifier_str))
              })*/
              
              //Endorsement require event
              sendEvent(EventType.PUBLISH_INFO, mediator, selfAddr, Topic.Endorsement, Event.Action.BLOCK_ENDORSEMENT)
            case false => logMsg(LogType.INFO, "Receives a wrong trans preload result with timeout error")
          }
        case false => logMsg(LogType.INFO, s"Block preload trans failed, error type : ${errorType} ~ ${error}")

      }
    /*case ResendEndorseInfo =>
      schedulerLink1 = clearSched1()
      if(blc.previousBlockHash.toStringUtf8() != pe.getCurrentBlockHash){
        resetVoteEnv
      }else{
        BlockTimeStatis4Times.setIsResendEndorse(true)
        
        schedulerLink1 = scheduler1.scheduleOnce(TimePolicy.getTimeoutEndorse seconds, self, ResendEndorseInfo)
        resendEndorser("/user/moduleManager/consensusManager/consensus-CRFD/endorse",PrimaryBlock(blc, pe.getBlocker,pe.getBlker_index,blkidentifier_str))
      }*/
    //块背书结果
    /*case EndorsedBlock(isSuccess, blc_new, endor,blkidentifier) =>
      var log_msg = ""
      logMsg(LOG_TYPE.WARN, s"recv endorsed from ${sender().toString()}")
      if(recvedEndorseAddr.isEmpty){
         BlockTimeStatis4Times.setFirstRecvEndorse(System.currentTimeMillis())
      }
      //ClusterHelper.isCandidateNow(sender().toString(), pe.getCandidator) match {
      ClusterHelper.isCandidateNow(pe.getSysTag, pe.getCandidator) match {
        case true =>
          //endorState match {
          IsFinishedEndorse == EndorseStatus.START_ENDORSE match {
            case true =>
              isSuccess match {
                case true =>
                  //Endorsement collection
                  val blcEn = blc.withConsensusMetadata(Seq())
                  //TODO kami 类似于MD5验证，是否是同一个blk（可以进一步的完善，存在效率问题？）
                  blkidentifier == blkidentifier_str match {
                    case true =>
                      BlockHelper.checkBlockContent(endor, Sha256.hash(blc_new.toByteArray)) match {
                        case true =>
                          if(!isExistEndorse(endor)){
                              addEndoserNode(akka.serialization.Serialization.serializedActorPath(sender()).toString(),"/user/moduleManager/consensusManager/consensus-CRFD/endorse")
                              blc = blc.withConsensusMetadata(blc.consensusMetadata.+:(endor))//在前面添加
                              logMsg(LOG_TYPE.INFO, s"node name=${pe.getSysTag},identifier=${blkidentifier},recv endorse, cert=${endor.endorser.toStringUtf8()}")
                              if (BlockHelper.checkCandidate(blc.consensusMetadata.length, pe.getCandidator.size)) {
                                schedulerLink1 = clearSched1()
                                
                                BlockTimeStatis4Times.setFinishEndorse(System.currentTimeMillis())
                                BlockTimeStatis4Times.printStatis4Times("BlockModule", pe.getSysTag)
                                
                                
                                self ! NewBlock(sender,blkidentifier)
                                recvedEndorseAddr.clear()
                                IsFinishedEndorse = EndorseStatus.END_ENDORSE
                                blkidentifier_str = ""
                                logTime("Endorsement collect end", CRFD_STEP._10_ENDORSE_COLLECTION_END,
                                  getActorRef(ActorType.STATISTIC_COLLECTION))
                              }
                              //广播收到背书信息的事件
                              sendEvent(EventType.RECEIVE_INFO, mediator, selfAddr, Topic.Block,
                                Event.Action.ENDORSEMENT)
                          }else{
                            logMsg(LOG_TYPE.INFO, s"recv Block endorse,this endorse exist,recver is = ${pe.getSysTag}")
                          }
                        case false => logMsg(LOG_TYPE.INFO, "Block endorse request data checked by cert failed")
                      }
                    case false => logMsg(LOG_TYPE.INFO, "Drop a endorsement by wrong block")
                  }
                case false => logMsg(LOG_TYPE.INFO, "Endorsement failed by trans checking")
              }
            case false => logMsg(LOG_TYPE.INFO, "Remote Endorsement of the block is enough, drop it")
          }
        case false => logMsg(LOG_TYPE.INFO, s"The sender is not a candidate this time, drop the endorsement form it. Sender:${sender()}")
      }*/

    //正式出块
    case NewBlock(tmpfinishblc,newblocker,blkidentifier) =>
      blc = tmpfinishblc
      if(blc.previousBlockHash.toStringUtf8() != pe.getCurrentBlockHash){
        resetVoteEnv
      }else{
        //调整确认块中的共识数据的顺序
        var consensus = blc.consensusMetadata.toArray[Endorsement]
        
        //var filterstart = System.nanoTime()
        var tmpconsensus = consensus.sortWith((endorser_left,endorser_right)=> endorser_left.endorser.toStringUtf8() < endorser_right.endorser.toStringUtf8())
        //var filterend = System.nanoTime()
        //logMsg(LOG_TYPE.INFO, s"filter sort spent time=${filterend-filterstart}")
        
        /*var javastart = System.nanoTime()
        sort(consensus)
        var javaend = System.nanoTime()
        logMsg(LOG_TYPE.INFO, s"java sort spent time=${javaend-javastart}")*/
        
        //var writestart = System.nanoTime()
        blc = blc.withConsensusMetadata(tmpconsensus)
        //var writeend = System.nanoTime()
        //logMsg(LOG_TYPE.INFO, s"write sort spent time=${writeend-writestart}")
        //广播这个block
        logMsg(LogType.INFO, s"new block,nodename=${pe.getSysTag},transaction size=${blc.transactions.size},identifier=${this.blkidentifier_str},${blkidentifier},current height=${dataaccess.getBlockChainInfo().height},previoushash=${blc.previousBlockHash.toStringUtf8()}")
        mediator ! Publish(Topic.Block, new ConfirmedBlock(blc, dataaccess.getBlockChainInfo().height + 1,
                            newblocker))
        logMsg(LogType.INFO, s"recv newBlock msg,node number=${pe.getSysTag},new block height=${dataaccess.getBlockChainInfo().height + 1}")                    
          
        //logTime("New block publish", CRFD_STEP._11_NEW_BLK_PUB, getActorRef(ActorType.STATISTIC_COLLECTION))
      }
    //接收广播的正式块数据
    case ConfirmedBlock(blk, height, actRef) =>
      //确认，接受新块（满足最基本的条件）
      //logTime("New block get and check", CRFD_STEP._12_NEW_BLK_GET_CHECK,
      //  getActorRef(ActorType.STATISTIC_COLLECTION))
      val endors = blk.consensusMetadata
      val blkOutEndorse = blk.withConsensusMetadata(Seq())
      if (BlockHelper.checkCandidate(endors.size, pe.getCandidator.size)) {
        var isEndorsed = true
        for (endorse <- endors) {
          //TODO kami 这是一个非常耗时的工作？后续需要完善
          if (!BlockHelper.checkBlockContent(endorse, Sha256.hash(blkOutEndorse.toByteArray))) isEndorsed = false
        }
        if (isEndorsed && BlockHelper.isEndorserListSorted(endors.toArray[Endorsement])==1) {
          //logTime("New block, start to store", CRFD_STEP._13_NEW_BLK_START_STORE,
          //  getActorRef(ActorType.STATISTIC_COLLECTION))
          //c4w 广播接收到block事件
          sendEvent(EventType.RECEIVE_INFO, mediator, selfAddr, Topic.Block, Event.Action.BLOCK_NEW)
          logTime("create block sendblock time", System.currentTimeMillis(),false)
          logTime("create block storeblock time", System.currentTimeMillis(),true)
          getActorRef(pe.getSysTag, ActorType.PERSISTENCE_MODULE) ! BlockRestore(blk, height, BlockSrc.CONFIRMED_BLOCK, actRef)
          //getActorRef(pe.getSysTag, ActorType.PERSISTENCE_MODULE) ! PersistenceModule.LastBlock(BlockHelper.getBlkHash(blk), 0,
          //  BlockSrc.CONFIRMED_BLOCK, self)
          if (isThisAddr(selfAddr, pe.getBlocker.toString)) {
            //Thread.sleep(500)
            logMsg(LogType.INFO, "block store finish,start new vote ...")
          }else{
            logMsg(LogType.INFO, s"blocker=${pe.getBlocker.toString},self=${selfAddr}")
          }
          logTime("blocker，from create to finish time", System.currentTimeMillis(),false)
          //接收到新块之后重新vote
          schedulerLink = clearSched()
          //状态设置改到持久化之后
          clearCache()
          logMsg(LogType.INFO, "New block, store opt over ...")
          //logTime("New block, store opt over", CRFD_STEP._14_NEW_BLK_STORE_END,
          //  getActorRef(ActorType.STATISTIC_COLLECTION))
        }
        else logMsg(LogType.WARN, s"The block endorsement info is wrong or endorser sort error. Sender : ${sender()}")
      }
      else logMsg(LogType.WARN, s"The num of  endorsement in block is not enough. Sender : ${sender()}")

    //创世块创建
    case GenesisBlock =>
      logMsg(LogType.INFO, "Create genesis block")
      blc = BlockHelper.genesisBlockCreator()
      isGensis = true
      getActorRef(pe.getSysTag, ActorType.PRELOADTRANS_MODULE) ! PreTransBlock(blc, PreTransFromType.BLOCK_GENSIS,"")
      //getActorRef(pe.getSysTag, ActorType.PRELOADTRANS_MODULE) ! PreTransBlock(pe.getBlk, PreTransFromType.BLOCK_GENSIS)

    //创世块预执行结果
    case PreTransBlockResultGensis(blk, result, merk, errorType, error) =>
      if (result) {
        blc = blk.withStateHash(ByteString.copyFromUtf8(merk))
        try {
          println(logPrefix + s"${pe.getSysTag} ~ Merk(Before Gensis): " + pe.getMerk)
          dataaccess.restoreBlocks(Array(blc))
          pe.resetSystemCurrentChainStatus(new BlockChainStatus(dataaccess.getBlockChainInfo().currentBlockHash.toStringUtf8,
                                          dataaccess.GetComputeMerkle4String,
                                          dataaccess.getBlockHeight()))
          
          println(logPrefix + s"${pe.getSysTag} ~ Merk(After Gensis): " + dataaccess.GetComputeMerkle4String)
          println("Gensis Block size is " + blc.toByteArray.size)
          //println("Gensis Block size is " + pe.getBlk.toByteArray.size)
          //创世块创建成功
          sendEvent(EventType.PUBLISH_INFO, mediator, selfAddr, Topic.Block, Event.Action.BLOCK_SYNC_SUC)
          //c4w Topic.xxx 用于代表实时图上的几个点，为指导绘图而设
          mediator ! Publish(Topic.Event, new Event(selfAddr, Topic.Block, Event.Action.BLOCK_NEW, Some(blc)))
          //mediator ! Publish(Topic.Event, new Event(selfAddr, Topic.Block, Event.Action.BLOCK_NEW, Some(pe.getBlk)))
          isGensis = false
          clearCache()
          getActorRef(pe.getSysTag, ActorType.VOTER_MODULE) ! NextVote(true,0,false)
        }
        catch {
          case e: Exception =>
            logMsg(LogType.ERROR, s"Commit error : ${e.toString}")
            logMsg(LogType.ERROR, "GensisBlock create failed, preload transaction error")
            clearCache()
            self ! GenesisBlock
          case _ => logMsg(LogType.ERROR, "Commit Error : Unknown")
        }
        finally {}
      }
      else {
        logMsg(LogType.WARN, "GensisBlock create failed, preload transaction error")
        self ! GenesisBlock
      }

    //同步结果
    case SyncResult(result, isStart, isStartNode, blkHash) =>
      isStart match {
        case true =>
          sendEvent(EventType.PUBLISH_INFO, mediator, selfAddr, Topic.Block, Event.Action.BLOCK_SYNC_SUC)
          isStartNode match {
            case true =>
              pe.getCacheHeight() match {
                case 0 =>
                  self ! GenesisBlock
                case _ =>
                  getActorRef(pe.getSysTag, ActorType.VOTER_MODULE) ! NextVote(true,0,false)
              }
            case false =>
              if (!result) logMsg(LogType.WARN,
                "System isn't start with sync successfully! Please shutdown and check it")
              else getActorRef(pe.getSysTag, ActorType.VOTER_MODULE) ! NextVote(true,0,false)
          }
        case false => getActorRef(pe.getSysTag, ActorType.VOTER_MODULE) ! NextVote(true,0,false)//ignore,目前没有这种情况
      }
    //出块超时
    case CreateBlockTimeOut =>
      //isBlocking match {
      pe.getIsBlocking() match {
        case true =>
          logMsg(LogType.WARN, "Create new block timeout check, failed")
          clearCache()
          schedulerLink = clearSched()
          getActorRef(pe.getSysTag, ActorType.VOTER_MODULE) ! NextVote(false,0,true)
        case false => 
          schedulerLink1 = clearSched1()
          schedulerLink = clearSched()
          //getActorRef(pe.getSysTag, ActorType.VOTER_MODULE) ! NextVote(false,0,true)
          logMsg(LogType.INFO, "Create block timeout check. successfully")
      }

    case _ => //ignore
  }

}