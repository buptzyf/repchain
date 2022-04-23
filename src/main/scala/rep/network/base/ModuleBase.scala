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


import akka.util.Timeout
import rep.network.cluster.ClusterActor
import rep.app.conf.TimePolicy
import rep.log.RepTimeTracer
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

abstract class  ModuleBase(name: String) extends ClusterActor{
  import scala.concurrent.duration._

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
    * @param timetag
    * @param time
    * @param isstart
    */
  def logTime(timetag:String,time:Long,isstart:Boolean,bheight:Long,trannum:Int): Unit = {
    if(isstart){
      RepTimeTracer.setStartTime(pe.getSysTag, timetag, time,bheight,trannum)
    }else{
      RepTimeTracer.setEndTime(pe.getSysTag, timetag, time,bheight,trannum)
    }
  }
}

