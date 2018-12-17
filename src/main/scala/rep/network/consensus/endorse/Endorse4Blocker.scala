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

import akka.actor.{ActorRef, Address, Props}
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import com.google.protobuf.ByteString
import rep.app.conf.{SystemProfile, TimePolicy}
import rep.crypto.ShaDigest
import rep.network.base.ModuleBase
import rep.network.cluster.ClusterHelper
import rep.protos.peer._
import rep.storage.ImpDataAccess
import rep.utils.GlobalUtils.{ActorType, BlockChainStatus, BlockEvent, EventType}

import scala.collection.mutable
import scala.util.control.Breaks
import rep.network.consensus.block.BlockTimeStatis4Times
import rep.network.consensus.block.BlockModule._
import rep.network.consensus.block.BlockHelper
import rep.network.consensus.CRFD.CRFD_STEP
import rep.network._
import rep.network.consensus.vote.CRFDVoterModule.NextVote
import rep.log.trace.LogType


object Endorse4Blocker {
  def props(name: String): Props = Props(classOf[Endorse4Blocker], name)
}

/**
  * 出块模块
  *
  * @author shidianyue
  * @version 1.0
  * @since 1.0
  * @param moduleName 模块名称
  **/
class Endorse4Blocker(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.concurrent.duration._
  import akka.actor.ActorSelection 
  import scala.collection.mutable.ArrayBuffer
  import rep.protos.peer.{Transaction}
  
  val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
  
  private var blc : Block = null
  private var blkidentifier_str = ""
  var IsFinishedEndorse :String= EndorseStatus.READY_ENDORSE
  private val recvedEndorseAddr = new mutable.HashMap[ String, String ]()
  
  def visitService(sn : Address, actorName:String,p:PrimaryBlock) = {  
        try {  
          val  selection : ActorSelection  = context.actorSelection(toAkkaUrl(sn , actorName));  
          selection ! p 
        } catch  {  
             case e: Exception => e.printStackTrace()
        }  
    }  
  
  def visitStoreService(sn : Address, actorName:String,cb:ConfirmedBlock) = {  
        try {  
          val  selection : ActorSelection  = context.actorSelection(toAkkaUrl(sn , actorName));  
          selection ! cb 
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
              if(this.selfAddr.indexOf(sn) == -1){
                visitService(sn , "/user/moduleManager/consensusManager/consensus-CRFD/endorse",p)
              }
            }
       })
  }
  
    def toAkkaUrl(sn : Address, actorName:String):String = {  
        return sn.toString + "/"  + actorName;  
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
  
    def resetEndorseInfo(blc_new:Block,bidentifier:String)={
      this.blc = blc_new
      this.blkidentifier_str = bidentifier
      IsFinishedEndorse = EndorseStatus.READY_ENDORSE
      schedulerLink = clearSched()
      
      //BlockTimeStatis4Times.clear
    }
    
    def resetVoteEnv1={
        this.blc = null
        this.blkidentifier_str = ""
        IsFinishedEndorse = EndorseStatus.READY_ENDORSE
        schedulerLink = clearSched()
        getActorRef(pe.getSysTag, ActorType.VOTER_MODULE) ! NextVote(true,0,false)
    }
    
  override def preStart(): Unit = {
    SubscribeTopic(mediator, self, selfAddr, Topic.Block, true)
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
    
  //case class endorsesendtime(systemName:String,start:Long,identify:String,prevhash:String,sendcount:Int)
  
  //var endorsesendtimer = endorsesendtime(pe.getSysTag,0l,"","",0)
  
  override def receive = {
    case ReadyEndorsement4Block(blc_new,bidentifier) =>
      logTime("create block endorse recv time", System.currentTimeMillis(),true)
      
      resetEndorseInfo(blc_new,bidentifier)
      schedulerLink = scheduler.scheduleOnce(TimePolicy.getTimeoutEndorse seconds, self, ResendEndorseInfo)
      IsFinishedEndorse = EndorseStatus.START_ENDORSE
      //endorsesendtimer = endorsesendtime(pe.getSysTag,System.currentTimeMillis(),blkidentifier_str,blc_new.previousBlockHash.toStringUtf8(),1)
      //BlockTimeStatis4Times.setStartEndorse(System.currentTimeMillis())
              
      pe.getStableNodes.foreach(sn=>{
        visitService(sn , "/user/moduleManager/consensusManager/consensus-CRFD/endorse",PrimaryBlock(blc, pe.getBlocker,pe.getBlker_index,blkidentifier_str,System.currentTimeMillis()))
      })
      
      
    case ResendEndorseInfo =>
      schedulerLink = clearSched()
      if(blc.previousBlockHash.toStringUtf8() != pe.getCurrentBlockHash){
        resetVoteEnv1
      }else{
        //BlockTimeStatis4Times.setIsResendEndorse(true)
        //println(s"-----resend---sysname=${endorsesendtimer.systemName},${pe.getSysTag};endorestimeout=${TimePolicy.getTimeoutEndorse};start=${endorsesendtimer.start},spend=${System.currentTimeMillis()-endorsesendtimer.start};"+
        //        s"hash=${endorsesendtimer.prevhash},${blc.previousBlockHash.toStringUtf8()};identify=${endorsesendtimer.identify},${blkidentifier_str}")
        //logMsg(LOG_TYPE.INFO, s"-----resend---sysname=${endorsesendtimer.systemName},${pe.getSysTag};start=${endorsesendtimer.start},spend=${System.currentTimeMillis()-endorsesendtimer.start};"+
        //        s"hash=${endorsesendtimer.prevhash},${blc.previousBlockHash.toStringUtf8()};identify=${endorsesendtimer.identify},${blkidentifier_str}")
        schedulerLink = scheduler.scheduleOnce(TimePolicy.getTimeoutEndorse seconds, self, ResendEndorseInfo)
        resendEndorser("/user/moduleManager/consensusManager/consensus-CRFD/endorse",PrimaryBlock(blc, pe.getBlocker,pe.getBlker_index,blkidentifier_str,System.currentTimeMillis()))
      }
      
      
    //块背书结果
    case EndorsedBlock(isSuccess, blc_new, endor,blkidentifier) =>
      var log_msg = ""
      logMsg(LogType.WARN, s"recv endorsed from ${sender().toString()}")
     // if(recvedEndorseAddr.isEmpty){
         //BlockTimeStatis4Times.setFirstRecvEndorse(System.currentTimeMillis())
      //}
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
                      BlockHelper.checkBlockContent(endor, ShaDigest.hash(blc_new.toByteArray)) match {
                        case true =>
                          if(!isExistEndorse(endor)){
                              addEndoserNode(akka.serialization.Serialization.serializedActorPath(sender()).toString(),"/user/moduleManager/consensusManager/consensus-CRFD/endorse")
                              blc = blc.withConsensusMetadata(blc.consensusMetadata.+:(endor))//在前面添加
                              logMsg(LogType.INFO, s"node name=${pe.getSysTag},identifier=${blkidentifier},recv endorse, cert=${endor.endorser.toStringUtf8()}")
                              if (BlockHelper.checkCandidate(blc.consensusMetadata.length, pe.getCandidator.size)) {
                                
                                logTime("create block endorse recv time", System.currentTimeMillis(),false)
                                schedulerLink = clearSched()
                                
                                //BlockTimeStatis4Times.setFinishEndorse(System.currentTimeMillis())
                                //BlockTimeStatis4Times.printStatis4Times("BlockModule", pe.getSysTag)
                                
                                
                                //getActorRef(ActorType.BLOCK_MODULE) ! NewBlock(blc,sender,blkidentifier)
                                //newBlock(blc,sender,blkidentifier)
                                
                                recvedEndorseAddr.clear()
                                IsFinishedEndorse = EndorseStatus.END_ENDORSE
                                blkidentifier_str = ""
                                //logTime("Endorsement collect end", CRFD_STEP._10_ENDORSE_COLLECTION_END,
                                //  getActorRef(ActorType.STATISTIC_COLLECTION))
                                
                                
         var consensus = blc.consensusMetadata.toArray[Endorsement]
        //var filterstart = System.nanoTime()
        //var tmpconsensus = consensus.sortWith((endorser_left,endorser_right)=> endorser_left.endorser.toStringUtf8() < endorser_right.endorser.toStringUtf8())
        //var filterend = System.nanoTime()
        //logMsg(LOG_TYPE.INFO, s"filter sort spent time=${filterend-filterstart}")
        
        var javastart = System.currentTimeMillis()
        sort(consensus)
        var javaend = System.currentTimeMillis()
        
        
        //var writestart = System.nanoTime()
        //sort(consensus)
        blc = blc.withConsensusMetadata(consensus)
        //var writeend = System.nanoTime()
        //logMsg(LOG_TYPE.INFO, s"write sort spent time=${writeend-writestart}")
        //广播这个block
        logMsg(LogType.INFO, s"new block,nodename=${pe.getSysTag},transaction size=${blc.transactions.size},identifier=${this.blkidentifier_str},${blkidentifier},current height=${dataaccess.getBlockChainInfo().height},previoushash=${blc.previousBlockHash.toStringUtf8()}")
        logTime("create block endorse time", System.currentTimeMillis(),false)
        logTime("create block sendblock time", System.currentTimeMillis(),true)
       
        
        
        mediator ! Publish(Topic.Block, new ConfirmedBlock(blc, dataaccess.getBlockChainInfo().height + 1,
                            sender))
        
     /*        pe.getStableNodes.foreach(sn=>{
                    visitStoreService(sn , "/user/moduleManager/consensusManager/consensus-CRFD/blocker",new ConfirmedBlock(blc, dataaccess.getBlockChainInfo().height + 1,
                            sender))                      
        })   */            
                    
        
        logMsg(LogType.INFO, s"java sort spent time=${javaend-javastart}")
        logMsg(LogType.INFO, s"recv newBlock msg,node number=${pe.getSysTag},new block height=${dataaccess.getBlockChainInfo().height + 1}")                    
          
        //logTime("New block publish", CRFD_STEP._11_NEW_BLK_PUB, getActorRef(ActorType.STATISTIC_COLLECTION))
                              }
                              //广播收到背书信息的事件
                              sendEvent(EventType.RECEIVE_INFO, mediator, selfAddr, Topic.Block,
                                Event.Action.ENDORSEMENT)
                          }else{
                            logMsg(LogType.INFO, s"recv Block endorse,this endorse exist,recver is = ${pe.getSysTag}")
                          }
                        case false => logMsg(LogType.INFO, "Block endorse request data checked by cert failed")
                      }
                    case false => logMsg(LogType.INFO, "Drop a endorsement by wrong block")
                  }
                case false => logMsg(LogType.INFO, "Endorsement failed by trans checking")
              }
            case false => logMsg(LogType.INFO, "Remote Endorsement of the block is enough, drop it")
          }
        case false => logMsg(LogType.INFO, s"The sender is not a candidate this time, drop the endorsement form it. Sender:${sender()}")
      }
  }
  
  
  /*private def newBlock(tmpfinishblc:Block,newblocker:ActorRef,blkidentifier:String)={
        var consensus = tmpfinishblc.consensusMetadata.toArray[Endorsement]
        
        //var filterstart = System.nanoTime()
        var tmpconsensus = consensus.sortWith((endorser_left,endorser_right)=> endorser_left.endorser.toStringUtf8() < endorser_right.endorser.toStringUtf8())
        //var filterend = System.nanoTime()
        //logMsg(LOG_TYPE.INFO, s"filter sort spent time=${filterend-filterstart}")
        
        /*var javastart = System.nanoTime()
        sort(consensus)
        var javaend = System.nanoTime()
        logMsg(LOG_TYPE.INFO, s"java sort spent time=${javaend-javastart}")*/
        
        //var writestart = System.nanoTime()
        blc = tmpfinishblc.withConsensusMetadata(tmpconsensus)
        //var writeend = System.nanoTime()
        //logMsg(LOG_TYPE.INFO, s"write sort spent time=${writeend-writestart}")
        //广播这个block
        logMsg(LOG_TYPE.INFO, s"new block,nodename=${pe.getSysTag},transaction size=${tmpfinishblc.transactions.size},identifier=${this.blkidentifier_str},${blkidentifier},current height=${dataaccess.getBlockChainInfo().height},previoushash=${tmpfinishblc.previousBlockHash.toStringUtf8()}")
        mediator ! Publish(Topic.Block, new ConfirmedBlock(tmpfinishblc, dataaccess.getBlockChainInfo().height + 1,
                            newblocker))
        logMsg(LOG_TYPE.INFO, s"recv newBlock msg,node number=${pe.getSysTag},new block height=${dataaccess.getBlockChainInfo().height + 1}")                    
          
        logTime("New block publish", CRFD_STEP._11_NEW_BLK_PUB, getActorRef(ActorType.STATISTIC_COLLECTION))
  }*/
  
}
