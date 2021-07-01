package rep.network.consensus.asyncconsensus.protocol.provablereliablebroadcast

import rep.network.consensus.asyncconsensus.common.crypto.ThresholdSignatureAPI
import rep.network.consensus.asyncconsensus.config.ConfigOfManager
import rep.protos.peer.DataOfStripe
import scala.collection.immutable.HashMap
import scala.collection.mutable.ArrayBuffer

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-03-13
 * @category	可验证的可靠广播数据管理类，存放协议过程的数据。
 */

class PRBCData(round:Long,rootHash:String,nodeName:String) {
  private var signTool : ThresholdSignatureAPI = null;
  //存放广播过来的数据，key==sender；value=data
  protected var echoStripes : HashMap[String,DataOfStripe] = new HashMap[String,DataOfStripe]()
  //存放已经接收的echo的放松者
  protected var echoSenders : ArrayBuffer[String] = new ArrayBuffer[String]()
  //是否已经发送了ready消息，初始化为false
  protected var readySent = false
  //存放已经接收的发送ready消息的发送者和签名信息，key=sender；value=sharesign
  protected var readySenders : ArrayBuffer[String] = new ArrayBuffer[String]()
  protected var readySigShares : HashMap[String,String] = new HashMap[String,String]()

  println(s"create prbc data nodename=${this.nodeName}")
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

  def hasRecvReadySender(sender:String):Boolean={
    this.readySenders.contains(sender)
  }

  def hasEchoData(sender:String):Boolean={
    if(this.echoStripes.get(sender) == None){
      false
    }else{
      true
    }
  }

  def getEchoCount:Int={
    this.echoSenders.length
  }

  def getReadyCount:Int={
    this.readySenders.length
  }

  def hasSentReady:Boolean={
    readySent
  }

  def sentReadyMsg:Unit={
    this.readySent = true
  }

  def saveEchoData(sender:String,data:DataOfStripe):Unit={
    this.echoSenders += sender
    this.echoStripes += sender -> data
  }

  def saveRecvReadyData(sender:String):Unit={
    this.readySenders += sender
  }

  def getStripes:Array[DataOfStripe]={
    this.echoStripes.values.toArray
  }

}
