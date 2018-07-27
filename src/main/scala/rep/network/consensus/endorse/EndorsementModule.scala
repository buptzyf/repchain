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
import scala.util.control.Breaks
import rep.network.consensus.block.BlockHelper
import scala.util.control.Exception.Finally

/**
  * Endorsement handler
  * Created by Kami on 2017/6/6.
  * @update 2018-05 jiangbuyun
  */

object EndorsementModule {
  def props(name: String): Props = Props(classOf[ EndorsementModule ], name)

  case object EndorseBlkTimeout

  case class BlockSyncResult(result: Boolean)

}

class EndorsementModule(moduleName: String) extends ModuleBase(moduleName) {

  import context.dispatcher
  import scala.concurrent.duration._
  import rep.protos.peer._
  import rep.storage.ImpDataAccess

  var cacheEndorseBlock : PrimaryBlock4Cache = null
  
  
  private def verifyTransSign(trans:Seq[Transaction]):Boolean={
    var r : Boolean = true
    val sr: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
    val loopbreak = new Breaks
        loopbreak.breakable(
            trans.foreach(f=>{
              if(!BlockHelper.checkTransaction(f, sr)){
              //if(!BlockHelper.verifySignForTranscation(f,pe.getSysTag)){
                r = false
                loopbreak.break
              }
            })
        )
    r
  }

  private  def hasRepeatOfTrans(trans:Seq[Transaction]):Boolean={
    var isRepeat : Boolean = false
    val aliaslist = trans.distinct
    if(aliaslist.size != trans.size){
      isRepeat = true 
    }else{
      val sr: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
      val loopbreak = new Breaks
        loopbreak.breakable(
            trans.foreach(f=>{
              if(sr.getBlockByTxId(f.txid) != null){
                isRepeat = true
                loopbreak.break
              }
            })
        )
    }
    isRepeat
  }
  
  

  private def endorseForWork(blk:Block, actRef: ActorRef,blkidentifier:String)={
      val dbinstancename = "endorse_"+blk.transactions.head.txid
      val preload: ImpDataPreload = ImpDataPreloadMgr.GetImpDataPreload(pe.getSysTag,dbinstancename)
      try {
          val blkData = blk.withConsensusMetadata(Seq())
          val blkInfo = Sha256.hash(blkData.toByteArray)
          if (BlockHelper.checkBlockContent(blk.consensusMetadata.head, blkInfo)) {
              if(!hasRepeatOfTrans(blk.transactions)){
                  if(verifyTransSign(blk.transactions)){
                    if(preload.VerifyForEndorsement(blk)){
                      logMsg(LOG_TYPE.WARN, s"Block endorse success,current height=${pe.getCacheHeight()},identifier=${blkidentifier}")
                      actRef ! EndorsedBlock(true, blkData, BlockHelper.endorseBlock(blkInfo, pe.getSysTag),blkidentifier)
                      //广播发送背书信息的事件(背书成功)
                      sendEvent(EventType.PUBLISH_INFO, mediator, selfAddr, Topic.Block, Event.Action.ENDORSEMENT)
                    }else{
                      logMsg(LOG_TYPE.WARN, "Transcation preload failed,Block endorse failed")
                      actRef! EndorsedBlock(false, blkData, BlockHelper.endorseBlock(blkInfo, pe.getSysTag),blkidentifier)
                    }
                  }else{
                    logMsg(LOG_TYPE.WARN, "Transcation Certification vertified failed")
                    actRef! EndorsedBlock(false, blkData, BlockHelper.endorseBlock(blkInfo, pe.getSysTag),blkidentifier)
                  }
              }else{
                    logMsg(LOG_TYPE.WARN, "Transcation vertified failed,found same Transcation")
                    actRef! EndorsedBlock(false, blkData, BlockHelper.endorseBlock(blkInfo, pe.getSysTag),blkidentifier)
              }
           }
           else {
             logMsg(LOG_TYPE.WARN, "Blocker Certification vertified failed")
             actRef! EndorsedBlock(false, blkData, BlockHelper.endorseBlock(blkInfo, pe.getSysTag),blkidentifier)
           }
         logTime("Endorse end", CRFD_STEP._9_ENDORSE_END, getActorRef(ActorType.STATISTIC_COLLECTION))
         logMsg(LOG_TYPE.WARN, s"Block endorse finish,current height=${pe.getCacheHeight()},identifier=${blkidentifier}")
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
  
  private def endorseForCheck(blk:Block, blocker:String, voteinedx:Int, actRef: ActorRef,blkidentifier:String)={
    if (!pe.getIsSync()) {
      //开始背书
        logTime("Endorse start", CRFD_STEP._8_ENDORSE_START, getActorRef(ActorType.STATISTIC_COLLECTION))
        println(pe.getSysTag + " " + pe.getPort + " request vote result for endorsement")
        //Check it's a candidate or not
        if (ClusterHelper.isCandidateNow(pe.getSysTag, pe.getCandidator)) {
            //Check the blocker
            println(" blk.previousBlockHash=" + blk.previousBlockHash.toStringUtf8  + "\t pe.getCurrentBlockHash="+pe.getCurrentBlockHash)
            if (blk.previousBlockHash.toStringUtf8 == pe.getCurrentBlockHash) {
              
                if (!ClusterHelper.isBlocker(pe.getSysTag, pe.getBlocker.toString)) {
                //Block endorsement
                //广播收到背书请求的事件
                sendEvent(EventType.RECEIVE_INFO, mediator, selfAddr, Topic.Endorsement, Event.Action.BLOCK_ENDORSEMENT)
                  if (pe.getBlocker.toString  == blocker.toString) {
                    endorseForWork(blk, actRef,blkidentifier)
                    cacheEndorseBlock = null
                  }else {
                    cacheEndorseBlock = PrimaryBlock4Cache(blk, blocker, voteinedx, actRef,blkidentifier)
                    NoticeVoteModule(voteinedx)
                    logMsg(LOG_TYPE.WARN, "endorse add to cache")
                    logMsg(LOG_TYPE.WARN, s"${blocker} is not the current blocker(${pe.getBlocker})")
                  }
                }else{ 
                  cacheEndorseBlock = null
                  logMsg(LOG_TYPE.WARN, s"Endorsement is from itself, dropped,systag=${pe.getSysTag},identifier=${blkidentifier},blocker=${pe.getBlocker},isBlocker=${ClusterHelper.isBlocker(pe.getSysTag, pe.getBlocker.toString)},current height=${pe.getCacheHeight()}")
                }
            }else{
                  //clear pe endorse cache
                  cacheEndorseBlock = PrimaryBlock4Cache(blk, blocker, voteinedx, actRef,blkidentifier)
                  logMsg(LOG_TYPE.WARN, "endorse add to cache")
                  logMsg(LOG_TYPE.WARN, s"Chain in this node is not complete,this current heigh=${pe.getCacheHeight()},before=${blk.previousBlockHash.toStringUtf8},local=${pe.getCurrentBlockHash},blocker=${pe.getBlocker})")
              }
        }
        else {
            cacheEndorseBlock = PrimaryBlock4Cache(blk, blocker, voteinedx, actRef,blkidentifier)
            //NoticeVoteModule(voteinedx)
          logMsg(LOG_TYPE.WARN, "It is not a candidate this time, endorsement requirement dropped")
        }
    }else{
      cacheEndorseBlock = null
      logMsg(LOG_TYPE.WARN, "Syncing, waiting for next endorsement request")
    }
  }
  
  
  override def receive = {
    //Endorsement block
    case PrimaryBlock(blk, blocker, voteinedx,blkidentifier) =>
      endorseForCheck(blk, blocker, voteinedx, sender(),blkidentifier)

    case RepeatCheckEndorseCache =>
      if(cacheEndorseBlock != null){
        endorseForCheck(cacheEndorseBlock.blc, cacheEndorseBlock.blocker, cacheEndorseBlock.voteinedx, cacheEndorseBlock.actRef,cacheEndorseBlock.blkidentifier)
      }
      
    case _ => //ignore
  }

}
