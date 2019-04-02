package rep.network.consensus.util

import rep.protos.peer._
import rep.crypto.cert.SignTool
import scala.util.control.Breaks._
import rep.crypto.Sha256
import com.google.protobuf.ByteString
import rep.utils.IdTool

object BlockVerify {
/****************************交易验证签名相关的操作开始**********************************************************/
  def VerifyOneSignOfTrans(t: Transaction, sysName: String): (Boolean, String) = {
    var result = false
    var resultMsg = ""

    try {
      val sig = t.signature
      val tOutSig = t.clearSignature
      result = SignTool.verify(sig.get.signature.toByteArray(), tOutSig.toByteArray, sig.get.getCertId, sysName)
    } catch {
      case e: RuntimeException =>
        result = false
        resultMsg = s"The transaction(${t.id}) is not trusted${e.getMessage}"
    }
    (result, resultMsg)
  }

  def VerifySignOfMultiTrans(trans: Seq[Transaction], sysName: String): (Boolean, String) = {
    var result = true
    var resultMsg = ""
    try {
      breakable(
        trans.foreach(f => {
          val r = VerifyOneSignOfTrans(f, sysName)
          if (!r._1) {
            result = false
            resultMsg = r._2
            break
          }
        }))
    } catch {
      case e: RuntimeException =>
        result = false
        resultMsg = s"The VerifySignOfMultiTrans error,info=${e.getMessage}"
    }
    (result, resultMsg)
  }

  def VerifyAllTransSignOfBlock(block: Block, sysName: String): (Boolean, String) = {
    try {
      VerifySignOfMultiTrans(block.transactions.toArray[Transaction], sysName)
    } catch {
      case e: RuntimeException => (false, s"Transaction's sign Error,info=${e.getMessage}")
    }
  }
/****************************交易验证签名相关的操作结束**********************************************************/

/****************************区块验证签名相关的操作开始**********************************************************/
  def VerifyOneEndorseOfBlock(endor: Signature, NonSignDataOfBlock: Array[Byte], sysName: String): (Boolean, String) = {
    var result = false
    var resultMsg = ""
    try {
      val certid = endor.getCertId
      result = SignTool.verify(endor.signature.toByteArray, NonSignDataOfBlock, certid, sysName)
    } catch {
      case e: RuntimeException =>
        result = false
        resultMsg = s"The endorsement  is not trusted${e.getMessage}"
    }
    (result, resultMsg)
  }

  def VerifyMutliEndorseOfBlock(endorses: Seq[Signature], NonSignDataOfBlock: Array[Byte], sysName: String): (Boolean, String) = {
    var result = true
    var resultMsg = ""
    try {
      breakable(
        endorses.foreach(f => {
          val r = VerifyOneEndorseOfBlock(f, NonSignDataOfBlock, sysName)
          if (!r._1) {
            result = false
            resultMsg = r._2
            break
          }
        }))
    } catch {
      case e: RuntimeException =>
        result = false
        resultMsg = s"The VerifySignOfMultiTrans error,info=${e.getMessage}"
    }
    (result, resultMsg)
  }

  def VerifyAllEndorseOfBlock(block: Block, sysName: String): (Boolean, String) = {
    try {
      val endors = block.endorsements
      val blkOutEndorse = block.clearEndorsements
      VerifyMutliEndorseOfBlock(endors, blkOutEndorse.toByteArray, sysName)
    } catch {
      case e: RuntimeException => (false, s"Endorsement's sign Error,info=${e.getMessage}")
    }
  }
/****************************区块验证签名相关的操作结束**********************************************************/

/****************************验证区块hash相关的操作开始**********************************************************/
  def VerifyHashOfBlock(block: Block): Boolean = {
    var result = false
    try {
      val oldhash = block.hashOfBlock.toStringUtf8()
      val blkOutEndorse = block.clearEndorsements
      val blkOutBlockHash = blkOutEndorse.withHashOfBlock(ByteString.EMPTY)
      val hash = Sha256.hashstr(blkOutBlockHash.toByteArray)
      if (oldhash.equals(hash)) {
        result = true
      }
    } catch {
      case e: RuntimeException =>
        result = false
    }
    result
  }
/****************************验证区块hash相关的操作结束**********************************************************/

/****************************检查背书是否完成开始**********************************************************/
  def EndorsementIsFinishOfBlock(block: Block, NodeNumber: Int): Boolean = {
    var result = false
    try {
      val endorseNumber = block.endorsements.size
      if ((endorseNumber - 1) >= Math.floor(((NodeNumber) * 1.0) / 2)) {
        result = true
      }
    } catch {
      case e: RuntimeException =>
        result = false
    }
    result
  }
/****************************检查背书是否完成结束**********************************************************/

/****************************验证背书信息是否排序的操作开始**********************************************************/
  def VerifyEndorserSorted(srclist: Array[Signature]): Int = {
    var b: Int = 0
    if (srclist == null || srclist.length < 2) {
      b
    } else {
      if (IdTool.getSigner4String(srclist(0).getCertId) < IdTool.getSigner4String(srclist(1).getCertId)) { //升序
        b = 1
      } else { //降序
        b = -1
      }

      breakable(
        for (i <- 1 to srclist.length - 1) {
          if (b == 1 && IdTool.getSigner4String(srclist(i).getCertId) < IdTool.getSigner4String(srclist(i - 1).getCertId)) {
            b = 0
            break
          }

          if (b == -1 && IdTool.getSigner4String(srclist(i).getCertId) > IdTool.getSigner4String(srclist(i - 1).getCertId)) {
            b = 0
            break
          }
        })
    }
    b
  }

  def VerifyTransactionSorted(srclist: Array[Transaction]): Int = {
    var b: Int = 0
    if (srclist == null || srclist.length < 2) {
      b
    } else {
      if (srclist(0).id < srclist(1).id) { //升序
        b = 1
      } else { //降序
        b = -1
      }

      breakable(
        for (i <- 1 to srclist.length - 1) {
          if (b == 1 && srclist(i).id < srclist(i - 1).id) {
            b = 0
            break
          }

          if (b == -1 && srclist(i).id > srclist(i - 1).id) {
            b = 0
            break
          }
        })
    }
    b
  }
/****************************验证背书信息是否排序的操作结束**********************************************************/
}