/**
 * @created zhaohuanjun 2020-03
*/
//zhj
package rep.network.consensus.pbft.endorse

import akka.actor.Props
import akka.util.Timeout
import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
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
  import rep.protos.peer._

  import scala.concurrent.duration._

  case class HashCommit(hash:ByteString, commit:MPbftCommit)


  private var recvedCommits = scala.collection.mutable.Buffer[HashCommit]()
  private var recvedCommitsCount = scala.collection.mutable.HashMap[ByteString, Int]()

  implicit val timeout = Timeout(TimePolicy.getTimeoutPreload.seconds)

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("PbftCommit Start"))
  }

  private def ProcessMsgPbftCommit(commit: MsgPbftCommit){
    val commits = recvedCommits.filter(_.hash == commit.block.hashOfBlock).map(f=>f.commit)
      .sortWith( (left,right)=> left.signature.get.certId.toString < right.signature.get.certId.toString)
    val bytes = MPbftReply().withCommits(commits).toByteArray
    //commits.map(f=>f.toByteString).reduce((a,f)=>a.concat(f))
    val certId = IdTool.getCertIdFromName(pe.getSysTag)
    val millis = TimeUtils.getCurrentTime()
    val sig = Signature(Option(certId),Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)),
      ByteString.copyFrom(SignTool.sign(pe.getSysTag, bytes)))
    var reply : MPbftReply = MPbftReply()
      .withCommits(commits)
      .withSignature(sig)

    val actor = context.actorSelection(commit.senderPath)
    actor ! MsgPbftReply(commit.block,reply,pe.getSystemCurrentChainStatus)

    recvedCommitsCount.remove(commit.block.hashOfBlock)
    commits.foreach(f=> recvedCommits -= HashCommit(commit.block.hashOfBlock, f))

  }

  override def receive = {

    case MsgPbftCommit(senderPath,block,blocker,commit,chainInfo) =>
      //RepLogger.print(RepLogger.zLogger,pe.getSysTag + ", PbftCommit recv commit: " + blocker + ", " + block.hashOfBlock)
      //already verified
      val hash = block.hashOfBlock
      recvedCommits += HashCommit(hash, commit)
      var count = 1
      if (recvedCommitsCount.contains(hash)) {
        count = recvedCommitsCount.get(hash).get +1
      }
      recvedCommitsCount.put(hash,count)
      if ( count >= (2*SystemProfile.getPbftF+1))
        ProcessMsgPbftCommit(MsgPbftCommit(senderPath,block,blocker,commit,chainInfo))

    case _ => //ignore
  }

}