package rep.network.consensus.asyncconsensus.protocol.consistentbroadcast

import rep.network.consensus.asyncconsensus.common.crypto.ThresholdSignatureAPI
import rep.network.consensus.asyncconsensus.config.ConfigOfManager
import scala.collection.mutable.ArrayBuffer

class CBCData(round:Long,nodeName:String) {
  private var signTool : ThresholdSignatureAPI = null;
  //存放已经接收的echo的放送者
  protected var echoSenders : ArrayBuffer[String] = new ArrayBuffer[String]()

  println(s"create cbc data nodename=${this.nodeName}")

  Init

  private def Init={
    try{
      val cfg = ConfigOfManager.getManager.getConfig(nodeName)
      this.signTool = new ThresholdSignatureAPI(cfg.getSignPublicKey23,cfg.getSignPrivateKey23)
    }catch {
      case e:Exception=>
        e.printStackTrace()
    }
  }

  def getSignTool:ThresholdSignatureAPI={
    this.signTool
  }


  def hasEchoSender(sender:String):Boolean={
    this.echoSenders.contains(sender)
  }

  def saveEchoSender(sender:String):Unit={
    this.echoSenders += sender
  }

  def getEchoSenderCount:Int={
    this.echoSenders.length
  }

}
