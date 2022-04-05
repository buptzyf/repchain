package rep.network.consensus.cfrdtream.util

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import rep.app.conf.{ TimePolicy}
import rep.network.consensus.byzantium.ConsensusCondition
import rep.network.consensus.util.{BlockHelp, BlockVerify}
import scala.collection.JavaConverters._
import rep.proto.rc2.{Block, Signature, Transaction}
import rep.utils.GlobalUtils.BlockerInfo
import scala.collection.mutable.ArrayBuffer

class BlockInfo4Stream(SystemName:String) {
  private var block: Block = null
  private var prevStateHash: String = null
  private var blockIndetifier: String = null
  private var createTime: Long = Long.MaxValue
  private var recvedEndorseInfo = new ConcurrentHashMap[String, EndorseResult4Stream]() asScala
  private var isConfirm: AtomicBoolean = new AtomicBoolean(false)
  private var isFinishVerify: AtomicBoolean = new AtomicBoolean()
  private var voteInfo: BlockerInfo = null

  def createNewBlock(prevHash: String, psHash: String, height: Long, trans: Seq[Transaction], vi: BlockerInfo): Block = {
    this.voteInfo = vi
    this.prevStateHash = psHash
    this.block = BlockHelp.WaitingForExecutionOfBlock(prevHash, height, trans)
    this.createTime = System.currentTimeMillis()
    this.isConfirm.set(false)
    this.recvedEndorseInfo.clear()
    this.block
  }

  def getBlock: Block = {
    this.block
  }

  def checkEndorsement: Boolean = {
    var r = false
    if (this.block != null && !this.block.header.get.hashPresent.isEmpty && !this.isFinishVerify.get()) {
      val vsize = this.verifyEndorseResult
      if(ConsensusCondition.ConsensusConditionChecked(vsize + 1)){
        this.recvedEndorseInfo.foreach(f => {
          this.block = BlockHelp.AddEndorsementToBlock(this.block, f._2.sign)
        })
        var consensus = this.block.header.get.endorsements.toArray[Signature]
        consensus = BlockVerify.sort(consensus)
        this.block = this.block.withHeader(this.block.header.get.withEndorsements(consensus))
        this.isFinishVerify.set(true)
        r = true
      }
    }
    r
  }


  private def verifyEndorseResult: Int = {
    var r = 0
    var err = new ArrayBuffer[String]()
    val bb = block.withHeader(block.header.get.clearEndorsements).toByteArray

    this.recvedEndorseInfo.foreach(f => {
      if (!f._2.isVerify) {
        try {
          val ev = BlockVerify.VerifyOneEndorseOfBlock(f._2.sign, bb, this.SystemName)
          if (ev._1) {
            f._2.isVerify = true
            r += 1
          } else {
            err += f._1
          }
        } catch {
          case e: Exception =>
            err += f._1
        }
      } else {
        r += 1
      }
    })
    err.foreach(k => {
      this.recvedEndorseInfo.remove(k)
    })
    r
  }

  def setConfirm = {
    this.isConfirm.set(true)
  }

  def hasConfirm: Boolean = {
    this.isConfirm.get()
  }

  def addEndorse(result: EndorseResult4Stream): Unit = {
    if(!this.isFinishVerify.get()){
      this.recvedEndorseInfo.put(result.endorseName, result)
      if(ConsensusCondition.ConsensusConditionChecked(this.recvedEndorseInfo.size + 1)){
        this.checkEndorsement
      }
    }
  }

  def hasFinishEndorse:Boolean={
    this.isFinishVerify.get()
  }

  def addEndorse4Self(blc: Block) = {
    if (this.block != null && this.block.header.get.height == blc.header.get.height
      && this.block.header.get.hashPrevious.toStringUtf8 == blc.header.get.hashPrevious.toStringUtf8
      && !this.block.header.get.hashPresent.isEmpty) {
      this.block = blc
    }
  }

  def isBlockTimeout: Boolean = {
    var r = false
    if (!this.isConfirm.get()) {
      if ((System.currentTimeMillis() - this.createTime > TimePolicy.getTimeOutBlock)) {
        r = true
      }
    }
    r
  }
}
