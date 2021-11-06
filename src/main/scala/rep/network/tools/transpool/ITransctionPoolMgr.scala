package rep.network.tools.transpool

import java.util.concurrent.ConcurrentSkipListMap

import rep.app.conf.{SystemProfile, TimePolicy}
import rep.log.RepLogger
import rep.protos.peer.Transaction
import rep.storage.ImpDataAccess
import rep.utils.SerializeUtils

import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks.{break, breakable}
import scala.collection.JavaConverters._

trait ITransctionPoolMgr {

  def packageTransaction(blockIdentifier: String, num: Int, sysName: String): Seq[Transaction]

  def rollbackTransaction(blockIdentifier: String)

  def cleanPreloadCache(blockIdentifier: String)

  def getTransListClone(num: Int, sysName: String): Seq[Transaction]

  def getTransListClone(start: Int, num: Int, sysName: String): Seq[Transaction]

  def putTran(tran: Transaction, sysName: String): Unit

  def findTrans(txid: String): Boolean

  def getTransaction(txid: String): Transaction

  def removeTrans(trans: Seq[Transaction], sysName: String): Unit

  def removeTranscation(tran: Transaction, sysName: String): Unit

  def getTransLength(): Int

  def isEmpty: Boolean

  def startupSchedule(sysName: String)

  def saveTransaction(sysName: String): Unit

  protected val txPrefix = "tx-buffer-on-shutdown"

  def readTransaction(sysName: String): Unit = {
    if (SystemProfile.getIsPersistenceTxToDB == 1) {
      var da = ImpDataAccess.GetDataAccess(sysName)
      try {
        val lbyte = da.Get(sysName + "-" + txPrefix)
        if (lbyte != null) {
          val ls = SerializeUtils.deserialise(lbyte)
          if (ls.isInstanceOf[ArrayBuffer[Array[Byte]]]) {
            val lsb = ls.asInstanceOf[ArrayBuffer[Array[Byte]]]
            lsb.foreach(bs => {
              val tx = Transaction.parseFrom(bs)
              this.putTran(tx, sysName)
            })
          }
        }
      } catch {
        case e: Exception =>
      } finally {
        da.Delete(sysName + "-" + txPrefix)
      }
    }
  }

}
