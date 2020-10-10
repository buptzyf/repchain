package rep.network.consensus.cfrdtream.sync.transaction

import akka.actor.{ActorRef, Props}
import rep.app.conf.SystemProfile
import rep.log.RepLogger
import rep.network.autotransaction.Topic
import rep.network.base.ModuleBase
import rep.network.consensus.cfrd.MsgOfCFRD
import rep.protos.peer.Transaction

object SyncTransaction{
  def props(name: String): Props = Props(classOf[SyncTransaction],name)
  //case class TreeNode(parent:TreeNode,left:TreeNode,right:TreeNode,)
}

class SyncTransaction (moduleName: String) extends ModuleBase(moduleName){

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("SyncTransaction module start"))
    super.preStart()
  }

  private def transactionIsExist(tid:String): Boolean ={
    pe.getTransPoolMgr.findTrans(tid)
  }

  private def handler(txid:String,recver:ActorRef)={
    if(this.transactionIsExist(txid)){
      val t = pe.getTransPoolMgr.getTransaction(txid)
      if(t != null) {
        RepLogger.trace(RepLogger.Business_Logger, this.getLogMsgPrefix(s"txid exist,send txid=${txid} ,from ${this.selfAddr} to ${akka.serialization.Serialization.serializedActorPath(recver)}"))
        recver ! t
      }else{
        RepLogger.error(RepLogger.Business_Logger, this.getLogMsgPrefix(s"txid not exist,do not send txid=${txid} , from ${this.selfAddr} to ${akka.serialization.Serialization.serializedActorPath(recver)}"))
      }
    }else{
      RepLogger.error(RepLogger.Business_Logger, this.getLogMsgPrefix(s"txid not exist,do not send txid=${txid} , from ${this.selfAddr} to ${akka.serialization.Serialization.serializedActorPath(recver)}"))
    }
  }

  override def receive = {
    //处理接收的交易
    case req: MsgOfCFRD.RequestTransaction =>
      handler(req.txid,req.recver)
    case reqs:MsgOfCFRD.RequestTransactions=>
      if(!reqs.txids.isEmpty){
        reqs.txids.foreach(f=>{
          handler(f,reqs.recver)
        })
      }
    case _ => //ignore
  }
}
