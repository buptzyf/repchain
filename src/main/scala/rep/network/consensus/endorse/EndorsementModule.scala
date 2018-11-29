/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
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

package rep.network.consensus.endorse

import akka.actor.{ActorRef,Props,Address}
import rep.crypto.Sha256
import rep.network.base.ModuleBase
import rep.network.consensus.block.BlockHelper
import rep.network.consensus.block.BlockModule._
import rep.network.Topic
import rep.network.cluster.ClusterHelper
import rep.network.consensus.CRFD.CRFD_STEP
import rep.protos.peer.{Event,Transaction}
import rep.storage.{ImpDataPreload, ImpDataPreloadMgr}
import rep.utils.GlobalUtils.{ActorType, BlockEvent, EventType}
import com.sun.beans.decoder.FalseElementHandler
import rep.network.consensus.vote.CRFDVoterModule.NextVote
import sun.font.TrueTypeFont
import scala.util.control.Breaks._
import rep.network.consensus.block.BlockHelper
import scala.util.control.Exception.Finally
import java.util.concurrent.ConcurrentHashMap
import rep.log.trace.LogType

/**
  * Endorsement handler
  * Created by Kami on 2017/6/6.
  * @update 2018-05 jiangbuyun
  */

object EndorsementModule {
  def props(name: String): Props = Props(classOf[ EndorsementModule ], name)

  case object EndorseBlkTimeout

  case class BlockSyncResult(result: Boolean)
  
  case object VerifySignTimeout

}

class EndorsementModule(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.concurrent.duration._
  import rep.protos.peer._
  import rep.storage.ImpDataAccess

  var cacheEndorseBlock : PrimaryBlock4Cache = null
  var waitEndorseblock : Block = null
  var waitEndorseblockIdentifier :String = ""
  var waitblockdata : Block = null
  var waitblockinfo : Array[Byte] = null
  var actRef4Blocker :ActorRef = null
  var recvendorserequesttime : Long = 0
  
  var verifyobject = new VerifySign4JavaWork(pe.getSysTag);
  
  /*val childnum = 10
  var vgActorRef : Array[ActorRef] = new Array[ActorRef](childnum)
  
  for(i <- 0 to childnum-1){
    vgActorRef(i) =  context.actorOf(verifySign4Endorment.props(pe.getSysTag+"-vg-endorse-"+i), pe.getSysTag+"-vg-endorse-"+i)
  }
  
  var verifyresult : Array[Integer] = new Array[Integer](childnum)
  
  private def clearVerifyResult={
    for( i <- 0 to childnum-1){
      verifyresult(i) = 0
    }
  }
  
  private def dispatchTransSignVerify(trans:Array[Transaction])={
    var size = trans.length
    if(size > childnum){
        var len = size / childnum
        var m = size % childnum
        for(i <- 0 to childnum-1){
          val startsend = System.currentTimeMillis()
            if(i == childnum-1){
                verifyresult(i) = 1
                vgActorRef(i) ! verifySign4Endorment.verifySign4Transcation(this.waitEndorseblockIdentifier,trans,i,len+m,i,System.currentTimeMillis())
            }else{
                verifyresult(i) = 1
                vgActorRef(i) ! verifySign4Endorment.verifySign4Transcation(this.waitEndorseblockIdentifier,trans,i,len,i,System.currentTimeMillis())
            }
          val endsend = System.currentTimeMillis()
          logMsg(LogType.INFO,s"+++++++send sign time=${endsend - startsend}")
        }
    }else{
      for(i <- 0 to size-1){
          verifyresult(i) = 1
          vgActorRef(i) ! verifySign4Endorment.verifySign4Transcation(this.waitEndorseblockIdentifier,trans,i,1,i,System.currentTimeMillis())
      }
    }
  }
  
  private def IsFinishSignVerify:Boolean={
    var r : Boolean = true
    breakable(
        this.verifyresult.foreach(f=>{
          if(f == 1){
            r = false
            break
          }
        })
    )
    r
  }
  
  private def setRecvVerify(idx:Integer)={
    if(idx <= childnum-1){
      this.verifyresult(idx) = 2
    }
  }
  
  private def RecvVerifyResutl(result:verifySign4Endorment.verifySignResult){
    if(result.blkhash == this.waitEndorseblockIdentifier){
        if(result.resultflag){
          if(result.actoridex <= childnum-1){
            setRecvVerify(result.actoridex)
            if(IsFinishSignVerify){
              val checktransresulttime = System.currentTimeMillis()
                      logMsg(LogType.WARN, s"Block endorse success,current height=${pe.getCacheHeight()},identifier=${this.waitEndorseblockIdentifier}")
                      //logMsg(LogType.INFO,s"handle time=${checktransresulttime-calltime},hashtime=${hashtime-calltime},checkblocktime=${checkblocktime-hashtime},"+
                      //    s"checktranstime=${checktranstime-checkblocktime},checktranssigntime=${checktranssigntime-checktranstime},checktransresulttime=${checktransresulttime-checktranssigntime}")
                      logMsg(LogType.INFO,s"+++++++endorse handle time=${System.currentTimeMillis() - this.recvendorserequesttime},endorse other time=${dispathtime-calltime},checkdispatchtime=${dispathtime-checktranstime}")
                      logMsg(LogType.INFO,s"+++++++hashtime=${hashtime-calltime},checkblocktime=${checkblocktime-hashtime},"+
                          s"checktranstime=${checktranstime-checkblocktime}}")
                      this.actRef4Blocker ! EndorsedBlock(true, this.waitblockdata, BlockHelper.endorseBlock(this.waitblockinfo, pe.getSysTag),this.waitEndorseblockIdentifier)
                      //广播发送背书信息的事件(背书成功)
                      sendEvent(EventType.PUBLISH_INFO, mediator, selfAddr, Topic.Block, Event.Action.ENDORSEMENT)
                      val finishtime = System.currentTimeMillis()
                      //logMsg(LogType.INFO,s"------endorse finish time=${finishtime-sendertime},network time=${recvtime-sendertime},condition check time=${calltime-recvtime},handle time=${finishtime-calltime}")
            }
          }
        }
    }
  }*/
  
  
  private def verifyTransSign(trans:Seq[Transaction]):Boolean={
    var r : Boolean = true
    val sr: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
    //val loopbreak = new Breaks
    //    loopbreak.breakable(
     breakable(
            trans.foreach(f=>{
             //从当前交易池查找该交易，如果存在该交易，不需要校验，已经校验过了
              if(!pe.findTrans(f.txid)){
                if(!BlockHelper.checkTransaction(f, sr)){
                //if(!BlockHelper.verifySignForTranscation(f,pe.getSysTag)){
                  r = false
                  //loopbreak.break
                  break
                }
              }
            })
        )
    r
  }
  
  private def findTransPoolTx(trans:Seq[Transaction]):Array[Int]={
    var buf : Array[Int] = new Array[Int](trans.size)
    var i = 0
    trans.foreach(f=>{
              if(pe.findTrans(f.txid)){
                 buf(i) = 1
              }else{
                buf(i) = 0
              }
              i += 1
        })
    buf
  }

  private  def hasRepeatOfTrans(trans:Seq[Transaction]):Boolean={
    var isRepeat : Boolean = false
    val aliaslist = trans.distinct
    if(aliaslist.size != trans.size){
      isRepeat = true 
    }else{
      val sr: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
      //val loopbreak = new Breaks
     //   loopbreak.breakable(
      breakable(
            trans.foreach(f=>{
              if(sr.getBlockByTxId(f.txid) != null){
                isRepeat = true
                //loopbreak.break
                break
              }
            })
        )
    }
    isRepeat
  }
  
  
          
  private def endorseForWork(blk:Block, actRef: ActorRef,blkidentifier:String,sendertime:Long,recvtime:Long)={
      val calltime = System.currentTimeMillis()
      val dbinstancename = "endorse_"+blk.transactions.head.txid
      val preload: ImpDataPreload = ImpDataPreloadMgr.GetImpDataPreload(pe.getSysTag,dbinstancename)
      try {
          val blkData = blk.withConsensusMetadata(Seq())
          val blkInfo = Sha256.hash(blkData.toByteArray)
           val hashtime = System.currentTimeMillis()
          if (BlockHelper.checkBlockContent(blk.consensusMetadata.head, blkInfo)) {
            val checkblocktime = System.currentTimeMillis()
              if(!hasRepeatOfTrans(blk.transactions)){
                 val checktranstime = System.currentTimeMillis()
                  if(preload.VerifyForEndorsement(blk)){
                    
                    /*this.waitEndorseblock = blk
                    this.waitEndorseblockIdentifier = blkidentifier
                    this.actRef4Blocker = actRef
                    this.waitblockinfo = blkInfo
                    this.waitblockdata = blkData
                    this.clearVerifyResult*/
                    val  checkresulttime = System.currentTimeMillis()
                    /*schedulerLink = scheduler.scheduleOnce(1 seconds, self, EndorsementModule.VerifySignTimeout )
                    dispatchTransSignVerify(blk.transactions.toArray[Transaction])*/
                    //使用多线程来验证签名 
                    //var findflag = findTransPoolTx(blk.transactions.toArray[Transaction])
                    //val b = verifyobject.StartVerify(blk.transactions.toArray[Transaction],findflag)
                    //使用对象内的方法来验证签名
                    val b =  verifyTransSign(blk.transactions.toArray[Transaction])
                     if(b){
                         val checksignresulttime = System.currentTimeMillis()
                        logMsg(LogType.WARN, s"Block endorse success,current height=${pe.getCacheHeight()},identifier=${blkidentifier}")
                        //logMsg(LogType.INFO,s"handle time=${checktransresulttime-calltime},hashtime=${hashtime-calltime},checkblocktime=${checkblocktime-hashtime},"+
                        //    s"checktranstime=${checktranstime-checkblocktime},checktranssigntime=${checktranssigntime-checktranstime},checktransresulttime=${checktransresulttime-checktranssigntime}")
                        logMsg(LogType.INFO,s"+++++++endorse handle time=${checksignresulttime - recvtime},endorse other time=${checkresulttime-calltime},sign time=${checksignresulttime-checkresulttime}")
                        //logMsg(LogType.INFO,s"+++++++hashtime=${hashtime-calltime},checkblocktime=${checkblocktime-hashtime},"+
                        //    s"checktranstime=${checktranstime-checkblocktime}}")
                        actRef ! EndorsedBlock(true, blkData, BlockHelper.endorseBlock(blkInfo, pe.getSysTag),blkidentifier)
                        //广播发送背书信息的事件(背书成功)
                        sendEvent(EventType.PUBLISH_INFO, mediator, selfAddr, Topic.Block, Event.Action.ENDORSEMENT)
                        val finishtime = System.currentTimeMillis()
                        //logMsg(LogType.INFO,s"------endorse finish time=${finishtime-sendertime},network time=${recvtime-sendertime},condition check time=${calltime-recvtime},handle time=${finishtime-calltime}")
                     }else{
                       actRef! EndorsedBlock(false, blkData, BlockHelper.endorseBlock(blkInfo, pe.getSysTag),blkidentifier)
                     }
                     //dispathtime = System.currentTimeMillis()
                    
                    
                    
                    /*if(verifyTransSign(blk.transactions)){
                      val checktransresulttime = System.currentTimeMillis()
                      logMsg(LogType.WARN, s"Block endorse success,current height=${pe.getCacheHeight()},identifier=${blkidentifier}")
                      logMsg(LogType.INFO,s"handle time=${checktransresulttime-calltime},hashtime=${hashtime-calltime},checkblocktime=${checkblocktime-hashtime},"+
                          s"checktranstime=${checktranstime-checkblocktime},checktranssigntime=${checktranssigntime-checktranstime},checktransresulttime=${checktransresulttime-checktranssigntime}")
                      actRef ! EndorsedBlock(true, blkData, BlockHelper.endorseBlock(blkInfo, pe.getSysTag),blkidentifier)
                      //广播发送背书信息的事件(背书成功)
                      sendEvent(EventType.PUBLISH_INFO, mediator, selfAddr, Topic.Block, Event.Action.ENDORSEMENT)
                      val finishtime = System.currentTimeMillis()
                      logMsg(LogType.INFO,s"------endorse finish time=${finishtime-sendertime},network time=${recvtime-sendertime},condition check time=${calltime-recvtime},handle time=${finishtime-calltime}")
                    }else{
                      logMsg(LogType.WARN, "Transcation Certification vertified failed")
                      actRef! EndorsedBlock(false, blkData, BlockHelper.endorseBlock(blkInfo, pe.getSysTag),blkidentifier)
                    }*/
                  }else{
                      val finishtime = System.currentTimeMillis()
                      logMsg(LogType.WARN, "Transcation preload failed,Block endorse failed")
                      logMsg(LogType.INFO,s"---failed---endorse finish time=${finishtime},network time=${recvtime-sendertime},condition check time=${calltime-recvtime},handle time=${finishtime-calltime}")
                      actRef! EndorsedBlock(false, blkData, BlockHelper.endorseBlock(blkInfo, pe.getSysTag),blkidentifier)
                    
                  }
              }else{
                    logMsg(LogType.WARN, "Transcation vertified failed,found same Transcation")
                    actRef! EndorsedBlock(false, blkData, BlockHelper.endorseBlock(blkInfo, pe.getSysTag),blkidentifier)
              }
           }
           else {
             logMsg(LogType.WARN, "Blocker Certification vertified failed")
             actRef! EndorsedBlock(false, blkData, BlockHelper.endorseBlock(blkInfo, pe.getSysTag),blkidentifier)
           }
         //logTime("Endorse end", CRFD_STEP._9_ENDORSE_END, getActorRef(ActorType.STATISTIC_COLLECTION))
         logMsg(LogType.WARN, s"Block endorse finish,current height=${pe.getCacheHeight()},identifier=${blkidentifier}")
      } catch {
      case e: Exception =>
        e.printStackTrace()
    }finally{
      if(preload != null){
        ImpDataPreloadMgr.Free(pe.getSysTag, dbinstancename)
      }
    }
  }
  
  def NoticeVoteModule(voteinedx:Int)={
      pe.setIsBlocking(false)
      pe.setEndorState(false)
      pe.setIsBlockVote(false)
      if(voteinedx>1){
        getActorRef(pe.getSysTag, ActorType.VOTER_MODULE) ! NextVote(false,voteinedx,false)
      }else{
        getActorRef(pe.getSysTag, ActorType.VOTER_MODULE) ! NextVote(true,0,false)
      }
  }
  
  private def endorseForCheck(blk:Block, blocker:String, voteinedx:Int, actRef: ActorRef,blkidentifier:String,sendertime:Long)={
    if (!pe.getIsSync()) {
      var recvtime = System.currentTimeMillis()
      //开始背书
        //logTime("Endorse start", CRFD_STEP._8_ENDORSE_START, getActorRef(ActorType.STATISTIC_COLLECTION))
        //println(pe.getSysTag + " " + pe.getPort + " request vote result for endorsement")
        //Check it's a candidate or not
        if (ClusterHelper.isCandidateNow(pe.getSysTag, pe.getCandidator)) {
            //Check the blocker
            //println(" blk.previousBlockHash=" + blk.previousBlockHash.toStringUtf8  + "\t pe.getCurrentBlockHash="+pe.getCurrentBlockHash)
            if (blk.previousBlockHash.toStringUtf8 == pe.getCurrentBlockHash) {
              
                if (!ClusterHelper.isBlocker(pe.getSysTag, pe.getBlocker.toString)) {
                //Block endorsement
                //广播收到背书请求的事件
                sendEvent(EventType.RECEIVE_INFO, mediator, selfAddr, Topic.Endorsement, Event.Action.BLOCK_ENDORSEMENT)
                  if (pe.getBlocker.toString  == blocker.toString) {
                    endorseForWork(blk, actRef,blkidentifier,sendertime,recvtime)
                    cacheEndorseBlock = null
                  }else {
                    cacheEndorseBlock = PrimaryBlock4Cache(blk, blocker, voteinedx, actRef,blkidentifier,sendertime)
                    NoticeVoteModule(voteinedx)
                    logMsg(LogType.WARN, "endorse add to cache")
                    logMsg(LogType.WARN, s"${blocker} is not the current blocker(${pe.getBlocker})")
                  }
                }else{ 
                  cacheEndorseBlock = null
                  logMsg(LogType.WARN, s"Endorsement is from itself, dropped,systag=${pe.getSysTag},identifier=${blkidentifier},blocker=${pe.getBlocker},isBlocker=${ClusterHelper.isBlocker(pe.getSysTag, pe.getBlocker.toString)},current height=${pe.getCacheHeight()}")
                }
            }else{
                  //clear pe endorse cache
                  cacheEndorseBlock = PrimaryBlock4Cache(blk, blocker, voteinedx, actRef,blkidentifier,sendertime)
                  logMsg(LogType.WARN, "endorse add to cache")
                  logMsg(LogType.WARN, s"Chain in this node is not complete,this current heigh=${pe.getCacheHeight()},before=${blk.previousBlockHash.toStringUtf8},local=${pe.getCurrentBlockHash},blocker=${pe.getBlocker})")
              }
        }
        else {
            cacheEndorseBlock = PrimaryBlock4Cache(blk, blocker, voteinedx, actRef,blkidentifier,sendertime)
            //NoticeVoteModule(voteinedx)
          logMsg(LogType.WARN, "It is not a candidate this time, endorsement requirement dropped")
        }
    }else{
      cacheEndorseBlock = null
      logMsg(LogType.WARN, "Syncing, waiting for next endorsement request")
    }
  }
  
  
  /*override def preStart(): Unit = {
    logMsg(LogType.INFO, "Endorsement module start")
    SubscribeTopic(mediator, self, selfAddr, Topic.Endorsement, true)
  }*/
  
  override def receive = {
    //Endorsement block
    case PrimaryBlock(blk, blocker, voteinedx,blkidentifier,sendertime) =>
        recvendorserequesttime = sendertime
        /*schedulerLink = clearSched()
        this.waitEndorseblock = null
        this.waitEndorseblockIdentifier = ""
        this.actRef4Blocker = null
        this.waitblockinfo = null
        this.waitblockdata = null
        this.clearVerifyResult*/
        endorseForCheck(blk, blocker, voteinedx, sender(),blkidentifier,sendertime)

    case RepeatCheckEndorseCache =>
      if(cacheEndorseBlock != null){
        recvendorserequesttime = cacheEndorseBlock.sendertime
        /*schedulerLink = clearSched()
        this.waitEndorseblock = null
        this.waitEndorseblockIdentifier = ""
        this.actRef4Blocker = null
        this.waitblockinfo = null
        this.waitblockdata = null
        this.clearVerifyResult*/
        endorseForCheck(cacheEndorseBlock.blc, cacheEndorseBlock.blocker, cacheEndorseBlock.voteinedx, cacheEndorseBlock.actRef,cacheEndorseBlock.blkidentifier,cacheEndorseBlock.sendertime)
      }
     
    case verifySign4Endorment.verifySignResult(blkhash,resultflag,startPos,lenght,actoridex) =>
      //RecvVerifyResutl(verifySign4Endorment.verifySignResult(blkhash,resultflag,startPos,lenght,actoridex))
      
    case EndorsementModule.VerifySignTimeout =>
        /*schedulerLink = clearSched()
        if(this.actRef4Blocker != null)
            this.actRef4Blocker! EndorsedBlock(false, this.waitblockdata, BlockHelper.endorseBlock(this.waitblockinfo, pe.getSysTag),this.waitEndorseblockIdentifier)
        this.waitEndorseblock = null
        this.waitEndorseblockIdentifier = ""
        this.actRef4Blocker = null
        this.waitblockinfo = null
        this.waitblockdata = null
        this.clearVerifyResult*/
    case _ => //ignore
  }

}
