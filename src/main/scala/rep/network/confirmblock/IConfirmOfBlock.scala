package rep.network.confirmblock


import akka.actor.{ActorRef, Props}
import akka.util.Timeout
import rep.log.{RepLogger, RepTimeTracer}
import rep.network.base.ModuleBase
import rep.network.consensus.common.MsgOfConsensus.{ConfirmedBlock}
import rep.network.consensus.util.BlockVerify
import scala.concurrent._

/**
 * Created by jiangbuyun on 2020/03/19.
 * 抽象的确认块actor
 */

object IConfirmOfBlock{
  def props(name: String): Props = Props(classOf[IConfirmOfBlock], name)
}

abstract  class IConfirmOfBlock(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.concurrent.duration._
  import rep.proto.rc2._

  implicit val timeout = Timeout(3.seconds)

  protected def asyncVerifyEndorse(e: Signature, byteOfBlock: Array[Byte]): Future[Boolean] = {
    val result = Promise[Boolean]

    val tmp = BlockVerify.VerifyOneEndorseOfBlock(e, byteOfBlock, pe.getSysTag)
    if (tmp._1) {
      result.success(true)
    } else {
      result.success(false)
    }
    result.future
  }

  protected def asyncVerifyEndorses(block: Block): Boolean = {
    val b = block.header.get.clearEndorsements.toByteArray
    val listOfFuture: Seq[Future[Boolean]] = block.header.get.endorsements.map(x => {
      asyncVerifyEndorse(x, b)
    })
    val futureOfList: Future[List[Boolean]] = Future.sequence(listOfFuture.toList).recover({
      case e: Exception =>
        null
    })

    val result1 = Await.result(futureOfList, timeout.duration).asInstanceOf[List[Boolean]]

    var result = true
    if (result1 == null) {
      false
    } else {
      result1.foreach(f => {
        if (!f) {
          result = false
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"comfirmOfBlock verify endorse is error, break,block height=${block.header.get.height},local height=${pe.getCurrentHeight}"))
        }
      })
    }

    result
  }

  protected def handler(block: Block, actRefOfBlock: ActorRef)

  protected def checkedOfConfirmBlock(block: Block, actRefOfBlock: ActorRef)

  override def receive = {
    case ConfirmedBlock(block, actRefOfBlock) =>
      RepTimeTracer.setStartTime(pe.getSysTag, "blockconfirm", System.currentTimeMillis(), block.header.get.height, block.transactions.size)
      checkedOfConfirmBlock(block, actRefOfBlock)
      RepTimeTracer.setEndTime(pe.getSysTag, "blockconfirm", System.currentTimeMillis(), block.header.get.height, block.transactions.size)
    case _ => //ignore
  }
}
