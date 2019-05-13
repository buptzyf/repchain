package rep.storage

import scala.util.control.Breaks._
import rep.log.RepLogger
import rep.protos.peer._
import rep.storage.block.BlockFileMgr

class Rollback4Storager(val dbop: ImpDataAccess,val filemgr: BlockFileMgr) {
  private var rollbackLockObject: Object = new Object()

  def rollbackToheight(toHeight: Long): Boolean = {
    var bv = true
    rollbackLockObject.synchronized {
      val chaininfo = dbop.getBlockChainInfo()
      var loop: Long = chaininfo.height
      breakable(
        while (loop > toHeight) {
          if (rollbackBlock(loop)) {
            loop -= 1
            RepLogger.trace(
              RepLogger.Storager_Logger,
              "system_name=" + dbop.getSystemName + s"\t  rollback block success ,rollback height=${loop}")
          } else {
            RepLogger.trace(
              RepLogger.Storager_Logger,
              "system_name=" + dbop.getSystemName + s"\t current rollback block happend error ,happend pos height=${loop},contract administrator!")
            bv = false
            break
          }
        })
    }
    bv
  }

  private def rollbackBlock(h: Long): Boolean = {
    var bv = false
    val block = dbop.getBlock4ObjectByHeight(h)
    val bidx = dbop.getBlockIdxByHeight(h)
    val txnumber = dbop.getBlockAllTxNumber()
    try {
      dbop.BeginTrans
      rollbackAllIndex(block)
      rollbackFileFirstHeight(bidx)
      rollbackTranIdxAndTranCount(bidx, txnumber)
      rollbackWorldState(block)
      filemgr.deleteBlockBytesFromFileTail(bidx.getBlockFileNo(), bidx.getBlockLength()+8)
      dbop.CommitTrans
      bv = true
    } catch {
      case e: Exception => {
        dbop.RollbackTrans
      }
    }
    bv
  }

  private def rollbackAllIndex(block: Block) = {
    dbop.Delete(IdxPrefix.IdxBlockPrefix + block.hashOfBlock.toStringUtf8())
    dbop.Delete(IdxPrefix.IdxBlockHeight + String.valueOf(block.height))
    dbop.Put(IdxPrefix.Height, String.valueOf(block.height - 1).getBytes())
  }

  private def rollbackFileFirstHeight(bidx: blockindex) = {
    val heightOfFile = dbop.getFileFirstHeight(bidx.getBlockFileNo())
    if (bidx.getBlockHeight() == heightOfFile) {
      dbop.rmFileFirstHeight(bidx.getBlockFileNo())
      dbop.Put(IdxPrefix.MaxFileNo, String.valueOf(bidx.getBlockFileNo() - 1).getBytes)
    }
  }

  private def rollbackTranIdxAndTranCount(bidx: blockindex, trancount: Long) = {
    var count = trancount
    val ts = bidx.getTxIds()
    if (ts != null && ts.length > 0) {
      ts.foreach(f => {
        dbop.Delete(IdxPrefix.IdxTransaction + f)
        count -= 1
      })
    }
    dbop.Put(IdxPrefix.TotalAllTxNumber, String.valueOf(count).getBytes())
  }

  private def getTxidFormBlock(block: Block, txid: String): String = {
    var rel = ""
    if (block != null) {
      var trans = block.transactions
      if (trans.length > 0) {
        trans.foreach(f => {
          if (f.id.equals(txid)) {
            rel = f.getCid.chaincodeName
          }
        })
      }
    }
    rel
  }

  private def rollbackWorldState(block: Block) = {
    try {
      val txresults = block.transactionResults
      if (!txresults.isEmpty) {
        txresults.foreach(f => {
          val txid = f.txId
          val cid = getTxidFormBlock(block, txid)
          val logs = f.ol

          if (logs != null && logs.length > 0) {
            logs.foreach(f => {
              var fkey = f.key
              if (!fkey.startsWith(IdxPrefix.WorldStateKeyPreFix)) {
                fkey = IdxPrefix.WorldStateKeyPreFix + cid + "_" + f.key
              }

              val oldvalue = f.oldValue
              if (oldvalue == null || oldvalue == _root_.com.google.protobuf.ByteString.EMPTY) {
                dbop.Delete(fkey)
              } else {
                dbop.Put(fkey, f.oldValue.toByteArray())
              }
            })
          }
        })
      }
    } catch {
      case e: RuntimeException => throw e
    }
  }

}