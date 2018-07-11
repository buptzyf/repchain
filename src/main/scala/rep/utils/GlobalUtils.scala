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
  case class BlockChainStatus(CurrentBlockHash:String,CurrentMerkle:String,CurrentHeight:Long)
  
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
    val MEMBER_LISTENER = 1
    val MODULE_MANAGER = 2
    val API_MODULE = 3
    val PEER_HELPER = 4
    val BLOCK_MODULE = 5
    val PRELOADTRANS_MODULE = 6
    val ENDORSE_MODULE = 7
    val VOTER_MODULE = 8
    val SYNC_MODULE = 9
    val TRANSACTION_POOL = 10
    val PERSISTENCE_MODULE = 11
    val CONSENSUS_MANAGER = 12
    val STATISTIC_COLLECTION = 13
  }

  case object EventType{
    val PUBLISH_INFO = 1
    val RECEIVE_INFO = 2
  }

  //

  val AppConfigPath = "application.conf"
  val SysConfigPath = "conf/system.conf"


}
