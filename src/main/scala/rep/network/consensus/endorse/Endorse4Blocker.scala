package rep.network.consensus.endorse

import akka.actor.{ActorRef, Address, Props}
import com.google.protobuf.ByteString
import rep.app.conf.{SystemProfile, TimePolicy}
import rep.crypto.Sha256
import rep.network.base.ModuleBase
import rep.network.cluster.ClusterHelper
import rep.protos.peer._
import rep.storage.ImpDataAccess
import rep.utils.GlobalUtils.{ActorType, BlockEvent, EventType,BlockChainStatus}
import scala.collection.mutable
import scala.util.control.Breaks
import rep.network.consensus.block.BlockTimeStatis4Times
import rep.network.consensus.block.BlockModule._ 
import rep.network.consensus.block.BlockHelper
import rep.network.consensus.CRFD.CRFD_STEP
import rep.network._
import rep.network.consensus.vote.CRFDVoterModule.NextVote


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
    
  override def receive = {
    case ReadyEndorsement4Block(blc_new,bidentifier) =>
      resetEndorseInfo(blc_new,bidentifier)
      schedulerLink = scheduler.scheduleOnce(TimePolicy.getTimeoutEndorse seconds, self, ResendEndorseInfo)
      IsFinishedEndorse = EndorseStatus.START_ENDORSE
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
        
        schedulerLink = scheduler.scheduleOnce(TimePolicy.getTimeoutEndorse seconds, self, ResendEndorseInfo)
        resendEndorser("/user/moduleManager/consensusManager/consensus-CRFD/endorse",PrimaryBlock(blc, pe.getBlocker,pe.getBlker_index,blkidentifier_str,System.currentTimeMillis()))
      }
      
      
    //块背书结果
    case EndorsedBlock(isSuccess, blc_new, endor,blkidentifier) =>
      var log_msg = ""
      logMsg(LOG_TYPE.WARN, s"recv endorsed from ${sender().toString()}")
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
                      BlockHelper.checkBlockContent(endor, Sha256.hash(blc_new.toByteArray)) match {
                        case true =>
                          if(!isExistEndorse(endor)){
                              addEndoserNode(akka.serialization.Serialization.serializedActorPath(sender()).toString(),"/user/moduleManager/consensusManager/consensus-CRFD/endorse")
                              blc = blc.withConsensusMetadata(blc.consensusMetadata.+:(endor))//在前面添加
                              logMsg(LOG_TYPE.INFO, s"node name=${pe.getSysTag},identifier=${blkidentifier},recv endorse, cert=${endor.endorser.toStringUtf8()}")
                              if (BlockHelper.checkCandidate(blc.consensusMetadata.length, pe.getCandidator.size)) {
                                schedulerLink = clearSched()
                                
                                //BlockTimeStatis4Times.setFinishEndorse(System.currentTimeMillis())
                                //BlockTimeStatis4Times.printStatis4Times("BlockModule", pe.getSysTag)
                                
                                
                                getActorRef(ActorType.BLOCK_MODULE) ! NewBlock(blc,sender,blkidentifier)
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
      }
  }
  
}
