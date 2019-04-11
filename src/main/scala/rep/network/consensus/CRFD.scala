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

package rep.network.consensus

import akka.actor.{ActorRef, Props}
import rep.network.base.{BaseActor}
import rep.network.consensus.CRFD.{ConsensusInitFinish, InitCRFD, NextConsensus}
import rep.sc.TransProcessor
import rep.utils.GlobalUtils.ActorType
import org.slf4j.LoggerFactory
import rep.log.trace._
import rep.network.consensus.block.{GenesisBlocker,ConfirmOfBlock,EndorseCollector,Blocker}
import rep.network.consensus.endorse.Endorser
import rep.network.consensus.transaction.PreloaderForTransaction
import rep.network.consensus.vote.Voter


/**
  * Consensus handle and message dispatcher
  * Created by shidianyue on 2017/9/23.
  * @update 2018-05 jiangbuyun
  */

object CRFD {
  def props(name: String): Props = Props(classOf[ CRFD ], name)

  case object InitCRFD

  case object StartCRFD

  case object ConsensusInitFinish

  case class NextConsensus(status:Boolean)

  case object CRFD_STEP{
    val _1_VOTE_START = 1
    val _2_VOTE_END = 2
    val _3_BLK_CREATE_START = 3
    val _4_BLK_CREATE_END = 4
    val _5_PRELOAD_START = 5
    val _6_PRELOAD_END = 6
    val _7_ENDORSE_PUB = 7
    val _8_ENDORSE_START = 8
    val _9_ENDORSE_END = 9
    val _10_ENDORSE_COLLECTION_END = 10
    val _11_NEW_BLK_PUB = 11
    val _12_NEW_BLK_GET_CHECK = 12
    val _13_NEW_BLK_START_STORE =13
    val _14_NEW_BLK_STORE_END = 14
  }

}

class CRFD(name: String) extends BaseConsenter  with BaseActor  {

  import scala.concurrent.duration._
  
  protected def log = LoggerFactory.getLogger(this.getClass)

  

  override def init(): Unit = {
//.withDispatcher("data-crfd-dispatcher")
    context.actorOf(Blocker.props("blocker"), "blocker")
    context.actorOf(GenesisBlocker.props("gensisblock"), "gensisblock")
    context.actorOf(ConfirmOfBlock.props("confirmerofblock"), "confirmerofblock")
    context.actorOf(EndorseCollector.props("endorsementcollectioner"), "endorsementcollectioner")
    context.actorOf(Endorser.props("endorser"), "endorser")
    context.actorOf(PreloaderForTransaction.props("preloaderoftransaction",context.actorOf(TransProcessor.props("sandbox", "", self),"sandboxProcessor")),"preloaderoftransaction")
    context.actorOf(Endorser.props("endorser"), "endorser")
    context.actorOf(Voter.props("voter"), "voter")
    

   
    //logMsg(LOG_TYPE.INFO,name,"CRFD init finished",selfAddr)
    //RepLogger.logInfo(pe.getSysTag, ModuleType.crfd, "CRFD init finished"+"~"+selfAddr)
  }

  override def initFinished(): Unit = {
    context.parent ! ConsensusInitFinish
  }

  override def start(): Unit = ???

  override def nextConsensus(): Unit = ???

  def nextConsensus(status:Boolean): Unit = {
    //getActorRef(ActorType.VOTER_MODULE) ! NextVote(status,0,false)
  }

  override def preStart(): Unit = {
    //logMsg(LOG_TYPE.INFO,name,"CRFD Actor Start",selfAddr)
    //RepLogger.logInfo(pe.getSysTag, ModuleType.crfd, "CRFD Actor Start"+"~"+selfAddr)
  }

  override def receive: Receive = {

    case InitCRFD =>
      init()

    //case BlockModuleInitFinished =>
    //  initFinished()

    case NextConsensus(status) =>
      nextConsensus(status)

    case _ => //ignore
  }
}
