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

package rep.network.consensus.vote

import akka.actor.{Actor, Address, Props}
import rep.app.conf.{SystemProfile, TimePolicy,SystemCertList}
import rep.crypto.Sha256
import rep.network.base.ModuleBase
import rep.network.consensus.CRFD.CRFD_STEP
import rep.network.consensus.vote.CRFDVoterModule._
import rep.protos.peer.BlockchainInfo
import rep.storage.ImpDataAccess
import rep.utils.GlobalUtils.{ActorType, BlockEvent}
import com.sun.beans.decoder.FalseElementHandler
import rep.log.trace.LogType

/**
  *
  * 抽签节点
  * 定时抽签/触发式抽签
  *
  * Created by kami on 2017/6/6.
  * 
  *  @update 2018-05 jiangbuyun
  */

object CRFDVoterModule {

  def props(name: String): Props = Props(classOf[ CRFDVoterModule ], name)

  //TODO kami 可以加一个index和flag。如果flag为false，则index+1，然后做check。如果为ture则继续投票
  case class NextVote(flag: Boolean,index:Int,isTimeout:Boolean)

  case object TickBlock

  case object TickVote

  case object VoteRecover

  final case class TickVoteInfo(clusterInfo: Map[ String, BlockchainInfo ])

  case class VoteResult(result: Boolean)

  case class Seed(hash: Array[ Byte ], blkSrc: String)

  case object SeedNode

  case object TransCheckResult {
    val SUC = 1
    val RETRY = 2
    val WAITING = 3
  }

  case object VoteConditionRecheck

}

class CRFDVoterModule(moduleName: String) extends ModuleBase(moduleName) with CRFDVoter{

  import context.dispatcher

  import scala.concurrent.duration._
  import scala.concurrent.forkjoin.ThreadLocalRandom

  val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)

  def rnd = ThreadLocalRandom.current

  var candidatorCur : Array[ String ] = null

  var isWaiting = false

  var isSeedNode = false

  var isRecovered = false

  var retryCount = 0

  var trans_num_threshold = 0

  var voteCount = 0
  
  def checkTranNumCondition(transCheckType: Int, curNum: Int, targetNum: Int, retryC: Int): Int = {
    if (curNum < targetNum) {
      if (retryC < SystemProfile.getRetryTime) TransCheckResult.RETRY
      else TransCheckResult.WAITING
    }
    else TransCheckResult.SUC
  }

  def resetTransThreshold(num: Int) = {
    this.trans_num_threshold = num
  }

  override def preStart(): Unit = {
    this.trans_num_threshold = SystemProfile.getMinBlockTransNum
  }

  // override postRestart so we don't call preStart and schedule a new Tick
  override def postRestart(reason: Throwable): Unit = ()

  def ArrayToString(a:Array[String]):String={
    var str = ""
    if(a != null && a.length>0){
      a.foreach(f=>{
        str += f +","
      })
    }
    str
  }
  
  override def receive = {
    case NextVote(flag,index,isTimeout) =>
      //println(pe.getSysTag + " vote request")
      retryCount = 0
      if (!pe.getIsBlockVote()) {
        if (pe.getStableNodes.size >= SystemProfile.getVoteNoteMin) {
          pe.setIsBlockVote(true)
          pe.setVoteBlockHash(pe.getCurrentBlockHash)
          schedulerLink = clearSched()
          if (flag) {
            pe.resetBlker_index
          }else {
            if(index == 0){
              if(isTimeout){
                pe.AddBlker_index
              }
            }else{
              pe.setBlker_index(index)
            }
          }
          self ! TickBlock
        }
        else {
          //TODO kami 可以考虑去掉该延迟
          schedulerLink = clearSched()
          schedulerLink = scheduler.scheduleOnce(TimePolicy.getVoteRetryDelay millis,
            self, NextVote(true,0,false))
        }
      }
      else {
        if(!pe.getCurrentBlockHash.equals(pe.getVoteBlockHash)){
          pe.setIsBlockVote(false)
          pe.setVoteBlockHash("0")
          self ! NextVote(flag,0,false)
        }
        logMsg(LogType.WARN, "Block voting now, waitting for next vote request")
      }

    case TickBlock =>
      logMsg(LogType.INFO, "Prepare vote")
      if (pe.getCacheBlkNum() > 0) {
        schedulerLink = clearSched()
        schedulerLink = scheduler.scheduleOnce(TimePolicy.getVoteRetryDelay millis,
          self, TickBlock)
        logMsg(LogType.INFO, "RetryDelay vote")
      }
      else {
        checkTranNumCondition(SystemProfile.getTransCreateType, pe.getTransLength(),
          trans_num_threshold, retryCount) match {
          case TransCheckResult.SUC =>
            logMsg(LogType.INFO, "Start vote")
            logTime("Vote time",System.currentTimeMillis(),true)
            //各节点执行出块人及候选人抽签
            val seed = pe.getVoteBlockHash//pe.getCurrentBlockHash
            
            candidatorCur = candidators(SystemCertList.getSystemCertList, Sha256.hash(seed))
            if (candidatorCur != null) {
              pe.resetCandidator(candidatorCur)
              if(pe.getCacheHeight() >= 1){
                  val blo = blocker(candidatorCur, pe.getBlker_index)
                  if (blo != null) {
                    pe.resetBlocker(blo)
                    //暂时只找到该方法能够明确标识一个在网络中（有网络地址）的节点，后续发现好方法可以完善
                    logMsg(LogType.INFO, s"Select  in getCacheHeight=${pe.getCacheHeight()} Candidates : ${ArrayToString(candidatorCur)} ~ Blocker : ${blo},currenthash=${pe.getCurrentBlockHash},index=${pe.getBlker_index}")
                    if (pe.getSysTag == blo) {
                      getActorRef(ActorType.BLOCK_MODULE) ! (BlockEvent.CREATE_BLOCK, "")
                      logMsg(LogType.INFO, s"Select  in getCacheHeight=${pe.getCacheHeight()} Candidates : ${ArrayToString(candidatorCur)} ,send Create cmd~ Blocker : ${blo},currenthash=${pe.getCurrentBlockHash},index=${pe.getBlker_index}")
                    }
                    else {
                      //如果不是自己则发送出块人地址
                      getActorRef(ActorType.BLOCK_MODULE) ! (BlockEvent.BLOCKER, selfAddr)
                      logMsg(LogType.INFO, s"Select  in getCacheHeight=${pe.getCacheHeight()} Candidates : ${ArrayToString(candidatorCur)} ,notice block module,~ Blocker : ${blo},currenthash=${pe.getCurrentBlockHash},index=${pe.getBlker_index}")
                    }
                  }
                  else {
                    //未选出候选人，是否需要避免该情况
                    logMsg(LogType.ERROR, "No Blocker selected")
                    //TODO kami 是否需要重新选举？
                    pe.resetBlker_index
                    schedulerLink = clearSched()
                    schedulerLink = scheduler.scheduleOnce(TimePolicy.getVoteRetryDelay millis,
                      self, TickBlock)
                  }
              }
              pe.setIsBlockVote(false)
              //voteblockhash = ""
              pe.setVoteBlockHash("0")
              resetTransThreshold(SystemProfile.getMinBlockTransNum)
              logTime("Vote time",System.currentTimeMillis(),false)
            }
            else {
              logMsg(LogType.INFO, "Not enough candidates")
              schedulerLink = clearSched()
              schedulerLink = scheduler.scheduleOnce(TimePolicy.getVoteRetryDelay millis,
                self, TickBlock)
            }
          case TransCheckResult.RETRY =>
            logMsg(LogType.INFO, "Not enough trans")
            //println("Not enough trans")
            retryCount += 1
            schedulerLink = clearSched()
            schedulerLink = scheduler.scheduleOnce(TimePolicy.getVoteRetryDelay millis,
              self, TickBlock)

          case TransCheckResult.WAITING =>
            logMsg(LogType.INFO, "Not enough trans, waiting for long time")
            isWaiting = true
            pe.setIsBlockVote(false)
            pe.setVoteBlockHash("0")
            schedulerLink = clearSched()
            schedulerLink = scheduler.scheduleOnce(TimePolicy.getVoteWaitingDelay millis,
              self, TickBlock)
        }
      }

    case SeedNode =>
      isSeedNode = true
      logMsg(LogType.INFO, "This is Seed Node")

    case VoteConditionRecheck =>
      if (isWaiting) {
        checkTranNumCondition(SystemProfile.getTransCreateType, pe.getTransLength(),
          trans_num_threshold, 0) match {
          case TransCheckResult.SUC =>
            isWaiting = false
            schedulerLink = clearSched()
            self ! NextVote(true,0,false)
          case TransCheckResult.RETRY =>
          //ignore
        }
      }

    //TODO kami 这里不直接改为1，应该进行recheck。而且还应该加个标志位。成功出块复核条件就更爱falg为ture。如果不成功则改为1.然后投票之后再改为配置项2
    case VoteRecover =>
      isWaiting match {
        case true =>
          resetTransThreshold(1)
          self ! VoteConditionRecheck
        case false => //ignore
      }

  }
}
