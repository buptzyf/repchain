/**
 * @created zhaohuanjun 2020-03
*/
//zhj
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
import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
import rep.app.Repchain
import rep.app.conf.{SystemProfile, TimePolicy}
import rep.crypto.cert.SignTool
import rep.log.RepLogger
import rep.network.base.ModuleBase
import rep.network.consensus.pbft.MsgOfPBFT.{MsgPbftCommit, MsgPbftPrepare}
import rep.utils.{IdTool, TimeUtils}

case object PbftPrepare {
  def props(name: String): Props = Props(classOf[PbftPrepare], name)
}

class PbftPrepare(moduleName: String) extends ModuleBase(moduleName) {
  import rep.protos.peer._

  import scala.concurrent.duration._

  private var recvedHash : ByteString = null
  private var recvedPrepares = scala.collection.mutable.Buffer[MPbftPrepare]()

  implicit val timeout = Timeout(TimePolicy.getTimeoutPreload.seconds)

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("PbftPrepare Start"))
  }

  private def ProcessMsgPbftPepare(prepare:MsgPbftPrepare) = {
    val prepares = recvedPrepares
      .sortWith( (left,right)=> left.signature.get.certId.toString < right.signature.get.certId.toString)
    val bytes = MPbftCommit().withPrepares(prepares).toByteArray//prepares.reduce((a,f)=>a.toByteString.concat(f.toByteString))
    val certId = IdTool.getCertIdFromName(pe.getSysTag)
    val millis = TimeUtils.getCurrentTime()
    val sig = Signature(Option(certId),Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)),
      ByteString.copyFrom(SignTool.sign(pe.getSysTag, bytes)))
    var commit : MPbftCommit = MPbftCommit()
      .withPrepares(prepares)
      .withSignature(sig)

    pe.getNodeMgr.getStableNodes.foreach(f => {
      val actorPath = f.toString + "/user/modulemanager/dispatchofRecvendorsement"
      val actor : ActorSelection = context.actorSelection(actorPath)
      actor ! MsgPbftCommit(prepare.senderPath,prepare.block,prepare.blocker,commit,pe.getSystemCurrentChainStatus)
    })

    recvedHash = null
    recvedPrepares.clear()
    val i = 0
  }

  override def receive = {

    case MsgPbftPrepare(senderPath,result, block, blocker, prepare, chainInfo) =>
          //already verified
          RepLogger.debug(RepLogger.zLogger,"R: " + Repchain.nn(sender) + "->" + Repchain.nn(pe.getSysTag) + ", PbftPrepare prepare: " + blocker + ", " + block.hashOfBlock.toStringUtf8)
          val hash = block.hashOfBlock
          if ( hash.equals(recvedHash)) {
            recvedPrepares += prepare
          } else {
            recvedHash = hash
            recvedPrepares.clear()
            recvedPrepares += prepare
          }
          if ( recvedPrepares.size >= 2*SystemProfile.getPbftF)
            ProcessMsgPbftPepare(MsgPbftPrepare(senderPath,result, block, blocker, prepare, chainInfo))

    case _ => //ignore
  }

}