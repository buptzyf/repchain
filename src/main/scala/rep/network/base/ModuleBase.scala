/*
 * Copyright  2019 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
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

package rep.network.base

import akka.actor.{Actor, ActorRef, Address}
import akka.util.Timeout
import rep.app.system.ClusterSystem
import rep.network.cluster.ClusterActor
import rep.network.tools.PeerExtension
import rep.crypto.Sha256

import scala.collection.mutable
import org.slf4j.LoggerFactory
import rep.app.conf.TimePolicy
import rep.log.RepTimeTracer
import rep.log.RepLogger
import rep.utils.GlobalUtils.BlockerInfo


/**
  * 模块基础类伴生对象
  *
  * @author shidianyue
  * @version 1.0
  * 
  * @update 2018-05 jiangbuyun
  **/
object ModuleBase {
}

/**
  * 系统模块基础类
  *
  * @author shidianyue
  * @version 1.0
  * @param name 模块名称
  **/

abstract class  ModuleBase(name: String) extends Actor  with ClusterActor with BaseActor{
  import scala.concurrent.duration._
  val pe = PeerExtension(context.system)
  /*val atype = ModuleNameToIntActorType
  atype match{
    case 0 => 
    case _ => 
      RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix(s"--------Actor create,actor name=${name}"))
      pe.register(atype, self)
  }*/
  
  /*private def ModuleNameToIntActorType:Int={
    name match{
      case "memberlistener" => 1
      case "modulemanager" => 2
      case "webapi" => 3
      case "peerhelper" => 4
      case "blocker" => 5
      case "preloaderoftransaction" => 6
      case "endorser" => 7
      case "voter" => 8
      case "synchrequester" => 9
      case "transactionpool" => 10
      case "storager" => 11
      case "synchresponser" => 12
      case "statiscollecter" => 13
      case "endorsementcollectioner" => 14
      //case "endorsementrequester" => 15
      case "confirmerofblock" => 16
      case "gensisblock"  => 17
      case "api" => 18
      case "transactiondispatcher" => 19
      case "dispatchofRecvendorsement" => 20
      case "dispatchofpreload" => 21
      case _ => 0
    }
  }*/
  //预执行的超时时间
  protected implicit val preload_timeout = Timeout((TimePolicy.getTimeoutPreload * 3).seconds)
  //背书超时时间
  protected implicit val endorse_timeout = Timeout((TimePolicy.getTimeoutEndorse * 3).seconds)
  //出块超时时间
  protected implicit val block_timeout = Timeout((TimePolicy.getTimeOutBlock * 3).seconds)
  //同步超时时间
  protected implicit val sync_chain_info_timeout = Timeout((TimePolicy.getTimeoutSync * 3).seconds)

  protected def isChangeBlocker(voteInfo:BlockerInfo):Boolean={
    isChangeBlocker(voteInfo,pe.getBlocker)
  }

  protected def isChangeBlocker(voteInfo1:BlockerInfo,voteInfo2:BlockerInfo):Boolean={
    var b = true
    if( voteInfo1.voteBlockHash == voteInfo2.voteBlockHash && voteInfo1.VoteHeight == voteInfo2.VoteHeight
      && voteInfo1.blocker == voteInfo2.blocker && voteInfo1.VoteIndex == voteInfo2.VoteIndex) {
      b = false
    }
    b
  }


  protected def isBlocker(voteInfo:BlockerInfo):Boolean={
    var b = false
    if(isChangeBlocker(voteInfo) && voteInfo.voteBlockHash == pe.getCurrentBlockHash){
      b = true
    }
    b
  }

  /**
    * 日志前缀
    *
    */
  def getLogMsgPrefix(msg:String):String = {
    s"${pe.getSysTag}~${this.name}~${msg}~"
  }
  
  /**
    * 事件时间戳封装
    * @param msg
    * @param step
    * @param actorRef
    */
  def logTime(timetag:String,time:Long,isstart:Boolean,bheight:Long,trannum:Int): Unit = {
    if(isstart){
      RepTimeTracer.setStartTime(pe.getSysTag, timetag, time,bheight,trannum)
    }else{
      RepTimeTracer.setEndTime(pe.getSysTag, timetag, time,bheight,trannum)
    }
  }
}

