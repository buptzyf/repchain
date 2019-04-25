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

package rep.network.consensus.endorse

import akka.actor.{ Address}
import rep.protos.peer.{Signature,Block,BlockchainInfo}
import rep.utils.GlobalUtils.{BlockerInfo}

object EndorseMsg {
  
  case object ResultFlagOfEndorse{
    val BlockerSelfError = 1
    val CandidatorError = 2
    val BlockHeightError = 3
    val VerifyError  = 4
    val success = 0
  }
  
  //背书请求者消息
  case class RequesterOfEndorsement(blc: Block, blocker: String, endorer: Address)
  
  //给背书人的背书消息
  case class EndorsementInfo(blc: Block, blocker: String)

  //背书收集者消息
  case class CollectEndorsement(blc: Block, blocker: String)

  //背书人返回的背书结果
  case class ResultOfEndorsed(result: Int, endor: Signature, BlockHash: String,endorserOfChainInfo:BlockchainInfo,endorserOfVote:BlockerInfo)

  //背书请求者返回的结果
  case class ResultOfEndorseRequester(result: Boolean, endor: Signature, BlockHash: String, endorser: Address)
  
  
 
}