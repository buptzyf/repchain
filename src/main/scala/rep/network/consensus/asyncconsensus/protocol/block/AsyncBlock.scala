package rep.network.consensus.asyncconsensus.protocol.block

import akka.actor.ActorRef
import rep.api.rest.RestActor.TransNumberOfBlock
import rep.network.consensus.asyncconsensus.protocol.block.AsyncBlock.{StartBlock, TPKEShare, TransactionsOfBlock}
import rep.network.consensus.asyncconsensus.protocol.msgcenter.Broadcaster
import rep.network.consensus.asyncconsensus.protocol.validatedcommonsubset.ValidatedCommonSubset.{AddDataToVCS, VCSB_Result}
import rep.protos.peer.Transaction
import rep.utils.SerializeUtils

import scala.collection.immutable.HashMap

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-02-13
 * @category	dumbo出块协议体的主要实现。
 */
object AsyncBlock{
  case class StartBlock(round:String,leader:String,transList:Seq[Transaction])
  case class TPKEShare(round:String,rootHash:String,ShareInfo:String)
  case class TransactionsOfBlock(round:String,transList:Seq[Transaction])
}

class AsyncBlock (nodeName:String,pathschema:String, moduleSchema:String, numberOfNodes:Int, numberOfFault:Int,
                  broadcaster: Broadcaster,caller:ActorRef,vcsb:ActorRef){

  private var data:HashMap[String,BlockData] = new HashMap[String,BlockData]()

  def StartBlockHandle(start:StartBlock):Unit={
    if(start.leader == this.nodeName){
      val d = this.getData(start.round)
      if(!d.getIsAddToVCS){
        d.setIsAddToVCS
        val data = SerializeUtils.serialise(start.transList)
        val cipherText = d.encrypt(data)
        val msg = new AddDataToVCS(start.leader,start.round,cipherText.getBytes())
        this.vcsb ! msg
      }
    }
  }

  def VCSB_Result_Handle(result:VCSB_Result):Unit={
    val d = this.getData(result.round)
    val value = result.content
    value.foreach(f=>{
      val rootHash = f._2._1
      val cipherText = f._2._2
      d.recvCipher(rootHash,new String(cipherText))
      val shareMsg = new TPKEShare(result.round,rootHash,d.shareDencrypt(rootHash))
      this.broadcaster.broadcastInSchema(this.pathschema,this.moduleSchema,shareMsg)
    })
  }

  def TPKEShareHandle(share:TPKEShare):Unit={
    val d = this.getData(share.round)
    if(!d.recvShareCipher(share.rootHash,share.ShareInfo)){
      d.addToMsg(share)
    }

    val msgs = d.getMsg
    var i = 0
    for(i<-0 to msgs.length-1){
      val msg = msgs(i)
      if(d.recvShareCipher(share.rootHash,share.ShareInfo)){
        d.delMsg(i)
        if(!d.hasContext(msg.rootHash) && d.hasDencrypt(msg.rootHash)){
          d.setContext(msg.rootHash,d.dencrypt(msg.rootHash))
        }
      }
    }

    if(!d.hasContext(share.rootHash) && d.hasDencrypt(share.rootHash)){
      d.setContext(share.rootHash,d.dencrypt(share.rootHash))
    }

    gettranList(d)
  }

  private def gettranList(d:BlockData):Unit = {
    var result : HashMap[String,Transaction] = new HashMap[String,Transaction]
    if(d.hasDencryptFinish){
      val cs = d.getAllContext
      cs.foreach(c=>{
        val translist = SerializeUtils.deserialise(c._2)
        val seqtrans = translist.asInstanceOf[Seq[Transaction]]
        seqtrans.foreach(t=>{
          if(!result.contains(t.id)){
            result += t.id -> t
          }
        })
      })
      println("&&&&&&&---node name="+this.nodeName)
      result.foreach(t=>{
        print(s"\t ${t._2.id}")
      })
      println("~~~~~"+result.valuesIterator.toSeq)
      this.caller ! new TransactionsOfBlock (d.getRound,result.valuesIterator.toSeq)
    }
  }

  private def getData(round:String):BlockData={
    if(!this.data.contains(round)){
      data += round -> new BlockData(this.nodeName,round)
    }
    data(round)
  }

}
