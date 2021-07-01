package rep.network.consensus.asyncconsensus.protocol.commoncoin

import akka.actor.ActorRef
import rep.network.consensus.asyncconsensus.common.crypto.ThresholdSignatureAPI
import rep.network.consensus.asyncconsensus.config.ConfigOfManager
//import rep.network.consensus.dumbo.protocol.commoncoin.CommonCoin.{ResultOfCommonCoin, ShareSignOfCommonCoin, StartCommonCoin}
import rep.protos.peer.{ResultOfCommonCoin, ShareSignOfCommonCoin, StartCommonCoin}

import scala.collection.immutable.HashMap

class Coin(val nodeName:String,val source:String,round:String,singleBit:Boolean) {
  private var signTool : ThresholdSignatureAPI = null;

  protected var recvInfo:HashMap[String,String] = new HashMap[String,String]()
  //protected var singleBit:Boolean = false
  protected var result:String = ""

  Init

  private def Init={
    try{
      val cfg = ConfigOfManager.getManager.getConfig(nodeName)
      this.signTool = new ThresholdSignatureAPI(cfg.getSignPublicKey13,cfg.getSignPrivateKey13)
    }catch {
      case e:Exception=>
        e.printStackTrace()
    }
  }

  def startup(msg: StartCommonCoin):ShareSignOfCommonCoin={
    val sign = this.signTool.shareSign(this.source)
    this.signTool.recvShareSign(sign,this.source)
    this.recvInfo += this.nodeName -> sign
    //this.singleBit = msg.singleBit
    new ShareSignOfCommonCoin(this.source,sign,this.nodeName,msg.round)
  }

  def handler(msg:ShareSignOfCommonCoin):ResultOfCommonCoin={
    if(this.result == ""){
      if(this.source == msg.source){
        if(!this.recvInfo.contains(msg.nodeName)){
          if(this.signTool.recvShareSign(msg.sign,this.source)){
            this.recvInfo += msg.nodeName -> msg.sign
          }
        }
      }
      if(this.signTool.hasCombine){
        val cbs = this.signTool.combineSign()
        if(this.signTool.verifyCombineSign(cbs,this.source)) {
          buildResult(cbs)
        }
      }

      if(result != "") {
        new ResultOfCommonCoin(this.source, this.result,this.round)
      }else{
        null
      }
    }else{
        new ResultOfCommonCoin(this.source,this.result,this.round)
    }
  }

  private def buildResult(cbs:String):Unit={
    if(this.singleBit){
      val ch = cbs.charAt(0).toInt % 2
      this.result = String.valueOf(ch)
    }else{
      this.result =  cbs
    }
  }

}
