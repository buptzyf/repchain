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

package rep.utils


import rep.protos.peer.{Transaction}
/**
  * 全局变量
  * Created by shidianyue on 2017/5/22.
  * 
  * @update 2018-05 jiangbuyun
  */
object GlobalUtils {
  case class TranscationPoolPackage(t:Transaction,createTime:Long)
  case class BlockerInfo(blocker:String,VoteIndex:Int,voteTime:Long)
  case object NodeStatus {
    val Blocking = 1
    val Endorsing = 2
    val Synching = 3
    val Ready = 4
    val Nothing  = 5
  }
  
  case object BlockEvent{
    //同步信息广播
    val CHAIN_INFO_SYNC = "CHAIN_INFO_SYNC"
    //创建block
    val CREATE_BLOCK = "CREATE_BLOCK"
    //出块人
    val BLOCKER = "BLOCKER"
    val BLOCK_HASH = "BLOCK_HASH"
    //创世块
    val GENESIS_BLOCK = "GENESIS_BLOCK"
    //出块成功
    val NEW_BLOCK = "NEW_BLOCK"
    //背书请求
    val BLOCK_ENDORSEMENT = "BLOCK_ENDORSEMENT"
    //背书反馈
    val ENDORSEMENT = "ENDORSEMENT"
    //出块确认
    val ENDORSEMENT_CHECK = "ENDORSEMENT_CHECK"
    //出块确认反馈
    val ENDORSEMENT_RESULT = "ENDORSEMENT_RESULT"
    //同步区块
    val BLOCK_SYNC = "BLOCK_SYNC"
    //同步区块数据
    val BLOCK_CHAIN = "BLOCK_CHAIN"
  }

  case object ActorType{
    val memberlistener = 1
    val modulemanager = 2
    val webapi = 3
    val peerhelper = 4
    val blocker = 5
    val preloaderoftransaction = 6
    val endorser = 7
    val voter = 8
    val synchrequester = 9
    val transactionpool = 10
    val storager = 11
    val synchresponser = 12
    val statiscollecter = 13
    val endorsementcollectioner = 14
    val endorsementrequester = 15
    val confirmerofblock = 16
    val gensisblock = 17
    val api = 18
    val preloadtransrouter = 19
  }
  
  

  case object EventType{
    val PUBLISH_INFO = 1
    val RECEIVE_INFO = 2
  }

  //

  val AppConfigPath = "application.conf"
  val SysConfigPath = "conf/system.conf"


}
