package rep.network.consensus.cfrdtream.sync.transaction

import akka.actor.Props
import com.google.protobuf.ByteString
import rep.log.RepLogger
import rep.network.base.ModuleBase
import rep.network.consensus.cfrd.MsgOfCFRD.PreloadTransaction
import rep.network.consensus.common.MsgOfConsensus.{PreTransBlock, PreTransBlockResult}
import rep.protos.peer.Transaction

import scala.collection.{breakOut, mutable}


object PreloaderOfTransaction{
  def props(name: String): Props = Props(classOf[PreloaderOfTransaction], name)
}

class PreloaderOfTransaction(moduleName: String) extends ModuleBase(moduleName) {
  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "PreloaderOfTransaction Start"))
  }

  override def receive = {
    case PreloadTransaction(txids,preBlockHash,height,dbtag) =>
     /* RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(  "entry preload"))
      if ((preBlockHash == pe.getCurrentBlockHash || preBlockHash == ByteString.EMPTY) &&
        height == (pe.getCurrentHeight + 1)) {
        var preLoadTrans = mutable.HashMap.empty[String, Transaction]
        //preLoadTrans = block.transactions.map(trans => (trans.id, trans))(breakOut): mutable.HashMap[String, Transaction]
        //preLoadTrans = block.transactions.map(trans => (trans.id, trans)).to(mutable.HashMap)  //.HashMap[String, Transaction]
        preLoadTrans = block.transactions.map(trans => (trans.id, trans))(breakOut): mutable.HashMap[String, Transaction]
        var transResult = Seq.empty[rep.protos.peer.TransactionResult]
        val dbtag = prefixOfDbTag+"_"+moduleName+"_"+block.transactions.head.id
        //确保提交的时候顺序执行
        block.transactions.map(t => {
          var ts = Handler(t, preLoadTrans, dbtag)
          if(ts != None){
            transResult = (transResult :+ ts.get)
          }
        })

        var newblock = AssembleTransResult(block,preLoadTrans,transResult,dbtag)

        if(newblock == None){
          //所有交易执行失败
          RepLogger.error(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s" All Transaction failed, error: ${block.height}"))
          sender ! PreTransBlockResult(null,false)
        }else{
          //全部或者部分交易成功
          sender ! PreTransBlockResult(newblock.get,true)
        }
      }*/
    case _ => //ignore
  }
}
