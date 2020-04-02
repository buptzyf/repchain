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
//zhj
package rep.network.cache.pbft

import akka.actor.Props
import rep.network.cache.ITransactionPool
import rep.network.consensus.pbft.MsgOfPBFT
import rep.network.consensus.pbft.MsgOfPBFT.VoteOfBlocker
import rep.network.consensus.pbft.vote.VoterOfPBFT
import rep.network.module.ModuleActorType
import rep.network.module.pbft.PBFTActorType

/**
 * 交易缓冲池伴生对象
 *
 * @author shidianyue
 * @version 1.0
 */
object TransactionPoolOfPBFT {
  def props(name: String): Props = Props(classOf[TransactionPoolOfPBFT], name)
  //交易检查结果
  case class CheckedTransactionResult(result: Boolean, msg: String)

}
/**
 * 交易缓冲池类
 *
 * @author shidianyue
 * @version 1.0
 * @param moduleName
 */

class TransactionPoolOfPBFT(moduleName: String)  extends ITransactionPool(moduleName) {
  override protected def sendVoteMessage: Unit = {
    pe.getActorRef(PBFTActorType.ActorType.voter) ! VoteOfBlocker("cache")
  }
}
