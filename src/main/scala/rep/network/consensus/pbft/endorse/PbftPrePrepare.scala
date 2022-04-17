/**
 * @created zhaohuanjun 2020-03
*/

package rep.network.consensus.pbft.endorse

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

import akka.actor.{ActorSelection, Props}
import akka.util.Timeout
import rep.app.Repchain
import rep.app.conf.TimePolicy
import rep.network.base.ModuleBase
import rep.log.RepLogger
import rep.network.consensus.pbft.MsgOfPBFT.{MPbftPrepare, MsgPbftPrePrepare, MsgPbftPrepare, ResultFlagOfEndorse}
import rep.network.consensus.util.BlockHelp

case object PbftPrePrepare {
  def props(name: String): Props = Props(classOf[PbftPrePrepare], name)
}

class PbftPrePrepare(moduleName: String) extends ModuleBase(moduleName) {
  import scala.concurrent.duration._

  implicit val timeout = Timeout(TimePolicy.getTimeoutPreload.seconds)

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("PbftPrePrepare Start"))
  }

  private def ProcessMsgPbftPrePrepare(prePrepare: MsgPbftPrePrepare): Unit = {
    pe.getNodeMgr.getStableNodes.foreach(f => {
      val actorPath = f.toString + "/user/modulemanager/dispatchofRecvendorsement"
      val actor : ActorSelection = context.actorSelection(actorPath)
      val prepare : MPbftPrepare = MPbftPrepare(Some(BlockHelp.SignBlock(prePrepare.block.getHeader, pe.getSysTag)))
      actor ! MsgPbftPrepare(prePrepare.senderPath, ResultFlagOfEndorse.success, prePrepare.block, prePrepare.blocker,prepare, pe.getSystemCurrentChainStatus)
    })
  }

  override def receive = {
    case MsgPbftPrePrepare(senderPath,block, blocker) =>
      RepLogger.debug(RepLogger.zLogger,"R: " + Repchain.nn(sender) + "->" + Repchain.nn(pe.getSysTag) + ", PbftPrePrepare preprepare: " + blocker + ", " + block.getHeader.hashPresent.toStringUtf8)
      ProcessMsgPbftPrePrepare(MsgPbftPrePrepare(senderPath, block, blocker))

    case _ => //ignore
  }

}