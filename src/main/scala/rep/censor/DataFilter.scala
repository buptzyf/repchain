package rep.censor

import rep.app.system.RepChainSystemContext
import rep.proto.rc2.{Block, Transaction}
import rep.storage.db.common.IDBAccess
import rep.storage.db.factory.DBFactory
import rep.utils.SerializeUtils

import scala.collection.mutable

class DataFilter(ctx: RepChainSystemContext, isEncrypt: Boolean = false) {
  private val db: IDBAccess = DBFactory.getDBAccess(ctx.getConfig)
  private val sha256 = ctx.getHashTool
  private val preKey = s"${ctx.getConfig.getChainNetworkId}_RegulateTPL___"
  private val isEnableFilter = ctx.getConfig.isEnableFilter

  /**
   * 过滤区块中的违规交易
   *
   * @param srcBlock 源区块
   * @return 过滤后的区块
   */
  def filterBlock(srcBlock: Block): Block = {
    // 先检查区块是否包含违规交易，如果区块中有违规交易，再逐个对交易筛查，避免一开始就对交易筛查影响效率
    if (isEnableFilter) {
      if (srcBlock != null) {
        if (checkBlock(srcBlock)) {
          val blockClearTrans = srcBlock.clearTransactions
          val transHashSet = mutable.LinkedHashSet.empty[Transaction]
          val transactions = srcBlock.transactions
          transactions.foreach(t => {
            if (checkTransaction(t)) {
              transHashSet.add(filterTran(t))
            } else {
              transHashSet.add(t)
            }
          })
          blockClearTrans.withTransactions(transHashSet.toSeq)
        } else {
          srcBlock
        }
      } else {
        null
      }
    } else {
      srcBlock
    }
  }

  /**
   * 过滤违规交易内容
   *
   * @param srcTran 源交易
   * @return 过滤后的交易
   */
  def filterTransaction(srcTran: Transaction): Transaction = {
    if (isEnableFilter) {
      if (checkTransaction(srcTran)) {
        filterTran(srcTran)
      } else {
        srcTran
      }
    } else {
      srcTran
    }
  }

  private def filterTran(srcTran: Transaction): Transaction = {
    val transHash = sha256.hashstr(srcTran.toByteArray)
    val tClearPara = srcTran.clearPara
    val iptClearArgs = srcTran.getIpt.clearArgs
    val cip = iptClearArgs.withArgs(Seq(srcTran.id, transHash))
    tClearPara.withIpt(cip)
  }

  /**
   * 检查交易是否违规
   *
   * @param srcTrans 源交易
   * @return true:违规; false:不违规
   */
  private def checkTransaction(srcTrans: Transaction): Boolean = {
    val key = s"$preKey${srcTrans.id}"
    val byte = db.getBytes(key)
    if (null == byte) {
      false
    } else {
      true
    }
  }

  /**
   * 检查区块是否包含违规内容
   *
   * @param block 源区块
   * @return true:违规; false:不违规
   */
  private def checkBlock(block: Block): Boolean = {
    var flag = false;
    val blockHash = block.getHeader.hashPresent.toStringUtf8
    val height = block.getHeader.height
    val illegalBlockKey = s"$preKey${blockHash}_$height"
    try {
      val bytes = db.getBytes(illegalBlockKey)
      flag = SerializeUtils.deserialise(bytes).asInstanceOf[Boolean]
    } catch {
      case e: Exception =>
    }
    flag
  }
}
