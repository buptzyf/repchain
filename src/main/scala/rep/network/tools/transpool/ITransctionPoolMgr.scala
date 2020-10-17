package rep.network.tools.transpool

import rep.app.conf.TimePolicy
import rep.log.RepLogger
import rep.protos.peer.Transaction
import rep.storage.ImpDataAccess

import scala.util.control.Breaks.{break, breakable}

trait ITransctionPoolMgr {
   def  packageTransaction(blockIdentifier:String,num: Int,sysName:String):Seq[Transaction]

   def rollbackTransaction(blockIdentifier:String)

    def cleanPreloadCache(blockIdentifier:String)

    def getTransListClone(num: Int,sysName:String): Seq[Transaction]
    def getTransListClone(start:Int,num: Int,sysName:String): Seq[Transaction]

    def putTran(tran: Transaction,sysName:String): Unit

    def findTrans(txid:String):Boolean
    def getTransaction(txid:String):Transaction

    def removeTrans(trans: Seq[ Transaction ],sysName:String): Unit

    def removeTranscation(tran:Transaction,sysName:String):Unit

    def getTransLength() : Int

    def isEmpty:Boolean

     def startupSchedule(sysName:String)
}
