package rep.network.consensus.cfrdtream

import akka.actor.Props
import rep.app.conf.SystemProfile
import rep.log.{RepLogger, RepTimeTracer}
import rep.network.autotransaction.Topic
import rep.network.base.ModuleBase
import rep.network.consensus.cfrd.MsgOfCFRD.{CollectEndorsement, CreateBlock}
import rep.network.module.cfrd.CFRDActorType
import rep.network.util.NodeHelp
import rep.protos.peer.{Block, Event, Transaction}
import rep.utils.GlobalUtils.EventType

import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks.{break, breakable}


object BlockerOfCFRDStream{
  def props(name: String): Props = Props(classOf[BlockerOfCFRDStream], name)
}

class BlockerOfCFRDStream(moduleName: String) extends ModuleBase(moduleName){
  /*var preblock: Block = null
  var transOfPacked : Seq[Transaction] = null

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("CFRDBlocker module start"))
    super.preStart()
  }

  protected def CollectedTransOfBlock(start: Int, num: Int, limitsize: Int) = {
    try {
      val tmplist = pe.getTransPoolMgr.getTransListClone(start, num, pe.getSysTag)
      if(tmplist.size > 0){
        this.transOfPacked = tmplist
      }
    } finally {
    }
  }

  private def CreateBlockHandler = {
    var blc : Block = null



    if (blc ==null)
      blc = PackedBlock(0)
    if (blc != null) {
      RepTimeTracer.setEndTime(pe.getSysTag, "createBlock", System.currentTimeMillis(), blc.height, blc.transactions.size)
      this.preblock = blc
      schedulerLink = clearSched()
      //在发出背书时，告诉对方我是当前出块人，取出系统的名称
      RepTimeTracer.setStartTime(pe.getSysTag, "Endorsement", System.currentTimeMillis(), blc.height, blc.transactions.size)
      //RepLogger.print(RepLogger.zLogger,"send CollectEndorsement, " + pe.getSysTag
      //  + ", " + pe.getCurrentBlockHash+ ", " + blc.previousBlockHash.toStringUtf8)
      pe.getActorRef(CFRDActorType.ActorType.endorsementcollectioner) ! CollectEndorsement(this.preblock, pe.getSysTag)
    } else {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("create new block error,CreateBlock is null" + "~" + selfAddr))
      pe.getActorRef(CFRDActorType.ActorType.voter) ! VoteOfBlocker
    }
  }
*/
  override def receive = {
    //创建块请求（给出块人）
    case CreateBlock =>
      /*if (!pe.isSynching) {
        if (NodeHelp.isBlocker(pe.getBlocker.blocker, pe.getSysTag) && pe.getBlocker.voteBlockHash == pe.getCurrentBlockHash) {
          sendEvent(EventType.PUBLISH_INFO, mediator, pe.getSysTag, Topic.Block, Event.Action.CANDIDATOR)
          //是出块节点
          if (preblock == null || (preblock.previousBlockHash.toStringUtf8() != pe.getBlocker.voteBlockHash)) {
            CreateBlockHandler
          }
        } else {
          //出块标识错误,暂时不用做任何处理
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,do not blocker or blocker hash not equal current hash,height=${pe.getCurrentHeight}" + "~" + selfAddr))
        }
      } else {
        //节点状态不对
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,node status error,status is synching,height=${pe.getCurrentHeight}" + "~" + selfAddr))
      }*/

    //zhjtps
    /* case CreateBlockTPS(ts : Seq[Transaction], trs : Seq[TransactionResult]) =>
       tsTPS = ts
       trsTPS = trs*/

    case _ => //ignore
  }
}
