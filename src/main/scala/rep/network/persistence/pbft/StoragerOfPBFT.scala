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

package rep.network.persistence

import akka.actor.Props
import rep.log.RepLogger
import rep.network.consensus.pbft.MsgOfPBFT.VoteOfBlocker
import rep.network.module.pbft.PBFTActorType

object StoragerOfPBFT {
  def props(name: String): Props = Props(classOf[StoragerOfPBFT], name)
}

class StoragerOfPBFT(moduleName: String) extends IStorager (moduleName: String) {

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Storager_Logger, this.getLogMsgPrefix( "Storager Start"))
  }

  override protected def sendVoteMessage: Unit = {
    pe.getActorRef( PBFTActorType.ActorType.voter) ! VoteOfBlocker("persistence")
  }

}