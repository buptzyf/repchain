/**
 * @created zhaohuanjun 2020-03
*/
//zhj
package rep.network.consensus.pbft.endorse

import akka.actor.Props
import akka.util.Timeout
import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
import rep.app.Repchain
import rep.app.conf.{SystemProfile, TimePolicy}
import rep.crypto.cert.SignTool
import rep.log.RepLogger
import rep.network.base.ModuleBase
import rep.network.consensus.pbft.MsgOfPBFT.{MsgPbftCommit, MsgPbftReply}
import rep.utils.{IdTool, TimeUtils}

case object PbftCommit {
  def props(name: String): Props = Props(classOf[PbftCommit], name)
}

class PbftCommit(moduleName: String) extends ModuleBase(moduleName) {
  import rep.proto.rc2._

  import scala.concurrent.duration._

  private var recvedHash : ByteString = null
  private var recvedCommits = scala.collection.mutable.Buffer[MPbftCommit]()

  implicit val timeout = Timeout(TimePolicy.getTimeoutPreload.seconds)

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("PbftCommit Start"))
  }

  private def ProcessMsgPbftCommit(commit: MsgPbftCommit){
    val commits = recvedCommits
      .sortWith( (left,right)=> left.signature.get.certId.toString < right.signature.get.certId.toString)
    val bytes = MPbftReply().withCommits(commits).toByteArray
    val certId = IdTool.getCertIdFromName(pe.getSysTag)
    val millis = TimeUtils.getCurrentTime()
    val sig = Signature(Option(certId),Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)),
      ByteString.copyFrom(SignTool.sign(pe.getSysTag, bytes)))
    var reply : MPbftReply = MPbftReply()
      .withCommits(commits)
      .withSignature(sig)

    val actor = context.actorSelection(commit.senderPath)
    actor ! MsgPbftReply(commit.block,reply,pe.getSystemCurrentChainStatus)

    recvedHash = null
    recvedCommits.clear()
  }

  override def receive = {

    case MsgPbftCommit(senderPath,block,blocker,commit,chainInfo) =>
      RepLogger.debug(RepLogger.zLogger,"R: " + Repchain.nn(sender) + "->" + Repchain.nn(pe.getSysTag) + ", PbftCommit commit: " + blocker + ", " + block.header.get.hashPresent.toStringUtf8)
      //already verified
      val hash = block.header.get.hashPresent
      if ( hash.equals(recvedHash)) {
        recvedCommits += commit
      } else {
        recvedHash = hash
        recvedCommits.clear()
        recvedCommits += commit
      }
      if ( recvedCommits.size >= (2*SystemProfile.getPbftF+1))
        ProcessMsgPbftCommit(MsgPbftCommit(senderPath,block,blocker,commit,chainInfo))

    case _ => //ignore
  }

}