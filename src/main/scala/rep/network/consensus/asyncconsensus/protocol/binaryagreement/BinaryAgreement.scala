package rep.network.consensus.asyncconsensus.protocol.binaryagreement

import akka.actor.ActorRef
import rep.network.consensus.asyncconsensus.protocol.binaryagreement.BinaryAgreement.{AUX, CONF, EST, RepeatBinaryAgreement, ResultOfBA, StartBinaryAgreement}
import rep.network.consensus.asyncconsensus.protocol.msgcenter.Broadcaster
import rep.protos.peer.{ResultOfCommonCoin, StartCommonCoin}
import scala.collection.mutable.HashMap

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-02-13
 * @category	二进制共识协议体的主要实现。
 */
object BinaryAgreement{
  case class StartBinaryAgreement(sendName:String,round:String,epoch:Int,binValue:Int)
  case class RepeatBinaryAgreement(sendName:String,round:String,epoch:Int,binValue:Int)
  case class EST(sendName:String,round:String,epoch:Int,binValue:Int)
  case class AUX(sendName:String,round:String,epoch:Int,binValue:Int)
  case class CONF(sendName:String,round:String,epoch:Int,confValue:String)
  case class ResultOfBA(round:String,epoch:Int,binValue:Int)
}

class BinaryAgreement(val nodeName:String, pathSchema:String,moduleSchema:String, nodeNumber:Int,faultNumber:Int,broadcaster: Broadcaster,ba:ActorRef,coin:ActorRef,caller:ActorRef) {
  private var moduleName = moduleSchema.replaceAll("\\{nodeName\\}",this.nodeName)
  private var data : HashMap[String,BAData] = new HashMap[String,BAData]()
  private var round : String = ""
  private var epoch : Int = -1
  private var isSendAux = false
  private var isSendCoin = false
  private var alreadyDecide = -1

  def StartHandle(msg : StartBinaryAgreement):Unit={
    println(s"~~~~~~~~~~start ba,msg:sendname=${msg.sendName},round=${msg.round},epoch=${msg.epoch},value=${msg.binValue},------recver=${this.nodeName}")
    if(msg.sendName == this.nodeName && (msg.binValue == 0 || msg.binValue == 1)){
      this.round = msg.round
      this.epoch = msg.epoch
      this.isSendAux = false
      this.isSendCoin = false
      this.alreadyDecide = -1
      //this.data = new HashMap[String,BAData]()
      val est = new EST(this.nodeName,msg.round,msg.epoch,msg.binValue)
      this.broadcaster.broadcastInSchema(this.pathSchema,this.moduleSchema,est)
    }
  }

  def RepeatHandle(repeatMsg:RepeatBinaryAgreement):Unit={
    println(s"----------repeat ba,msg:sendname=${repeatMsg.sendName},round=${repeatMsg.round},epoch=${repeatMsg.epoch},value=${repeatMsg.binValue},------recver=${this.nodeName}")
    if(repeatMsg.sendName == this.nodeName && (repeatMsg.binValue == 0 || repeatMsg.binValue == 1)){
      this.isSendAux = false
      this.isSendCoin = false
      this.round = repeatMsg.round
      this.epoch = repeatMsg.epoch
      val est = new EST(this.nodeName,repeatMsg.round,repeatMsg.epoch,repeatMsg.binValue)
      this.broadcaster.broadcastInSchema(this.pathSchema,this.moduleSchema,est)
    }
  }

  def EstHandle(estMsg:EST): Unit ={
    println(s"###########EST ba,msg:sendname=${estMsg.sendName},round=${estMsg.round},epoch=${estMsg.epoch},value=${estMsg.binValue},------recver=${this.nodeName}")
    if(estMsg.binValue == 0 || estMsg.binValue == 1){
      val d = getData(estMsg.round,estMsg.epoch)
      if(!d.isRecvEstSender(estMsg.binValue,estMsg.sendName)){
        d.saveEstSender(estMsg.binValue,estMsg.sendName)
      }
      if(d.getEstCount(estMsg.binValue) >= this.faultNumber+1 &&  !d.isSentEst(estMsg.binValue)){
        d.setEstSent(estMsg.binValue)
        val est = new EST(this.nodeName,estMsg.round,estMsg.epoch,estMsg.binValue)
        this.broadcaster.broadcast(this.pathSchema,this.moduleSchema,est)
      }

      if(d.getEstCount(estMsg.binValue) >= 2*this.faultNumber + 1){
        d.addBinValue(estMsg.binValue)
        if(!this.isSendAux){
          this.isSendAux = true
          val aux = new AUX(this.nodeName,estMsg.round,estMsg.epoch,d.getBinValueForFirst)
          println(s"*************start AUX ba,msg:sendname=${aux.sendName},round=${aux.round},epoch=${aux.epoch},value=${aux.binValue},------recver=${this.nodeName}")
          this.broadcaster.broadcastInSchema(this.pathSchema,this.moduleSchema,aux)
        }
      }
    }
  }

  def AuxHandle(auxMsg:AUX): Unit={
    println(s"~~~~~~~~~~AUX ba,msg:sendname=${auxMsg.sendName},round=${auxMsg.round},epoch=${auxMsg.epoch},value=${auxMsg.binValue},------recver=${this.nodeName}")
    if(auxMsg.binValue == 0 || auxMsg.binValue == 1){
      val d = getData(auxMsg.round,auxMsg.epoch)
      if(!d.isRecvAuxSender(auxMsg.binValue,auxMsg.sendName)){
        d.saveAuxSender(auxMsg.binValue,auxMsg.sendName)
      }

      var value : String = ""
      if(d.containsInBinValue(1) && d.getAuxCount(1) >= this.nodeNumber-this.faultNumber){
        value = "1"
      }else if(d.containsInBinValue(0) && d.getAuxCount(0) >= this.nodeNumber-this.faultNumber){
        value = "0"
      }else if(d.getAuxCount(0) + d.getAuxCount(1) >= this.nodeNumber-this.faultNumber){
        value = "0_1"
      }

      if(value != ""){
        d.setConfSent(value)
        val conf = new CONF(this.nodeName,auxMsg.round,auxMsg.epoch,value)
        println(s"%%%%%%%%%%%%%start CONF ba,msg:sendname=${conf.sendName},round=${conf.round},epoch=${conf.epoch},value=${conf.confValue},------recver=${this.nodeName}")
        this.broadcaster.broadcastInSchema(this.pathSchema,this.moduleSchema,conf)
      }
    }
  }

  def ConfHandle(confMsg:CONF): Unit={
    println(s"~~~~~~~~~~AUX ba,msg:sendname=${confMsg.sendName},round=${confMsg.round},epoch=${confMsg.epoch},value=${confMsg.confValue},------recver=${this.nodeName}")
    if(confMsg.confValue == "0" || confMsg.confValue == "1" || confMsg.confValue == "0_1") {
      val d = getData(confMsg.round, confMsg.epoch)
      if (!d.isRecvConfValue(confMsg.confValue, confMsg.sendName)) {
        d.saveConfSender(confMsg.confValue, confMsg.sendName)
      }

      var value : String = ""
      if(d.containsInBinValue(1) && d.getConfCount("1") >= this.nodeNumber-this.faultNumber){
        value = "1"
        d.setConfValueOut(value)
      }else if(d.containsInBinValue(0) && d.getConfCount("0") >= this.nodeNumber-this.faultNumber){
        value = "0"
        d.setConfValueOut(value)
      }else if(d.getConfCount("0") + d.getConfCount("1") + d.getConfCount("0_1") >= this.nodeNumber-this.faultNumber){
        value = "0_1"
        d.setConfValueOut(value)
      }

      //todod 
      if(value != "" && !this.isSendCoin){
        this.isSendCoin = true
        println(s"++++++++++++COIN ba,------recver=${this.nodeName}")
        this.coin ! new StartCommonCoin(this.round + "_"+this.epoch,true,confMsg.round)
      }
    }
  }

  def ResultOfCommonCoinHandle(coin:ResultOfCommonCoin):Unit={
    println(s"~~~~~~~~~~resut ba,msg:result=${coin.result},source=${coin.source},round=${coin.round},------recver=${this.nodeName}")

    if(coin.source == this.round + "_"+this.epoch){
      val coinvalue = coin.result.toInt
      val d = getData(this.round, this.epoch)
      val value = d.getConfValueOut

      println(s"==========BA resut ba,source=${coin.source},msg:coinvalue=${coinvalue},value=${value},alreadyDecide=${alreadyDecide},------recver=${this.nodeName}")

      if(value == "0" || value == "1"){
        if(value == coinvalue.toString){
          println(s"==========BA resut ba,source=${coin.source},value == coinvalue.toString:${value} == ${coinvalue.toString}------recver=${this.nodeName}")
          if(this.alreadyDecide == -1){
            println(s"==========BA resut ba,set already,source=${coin.source},value != coinvalue.toString:${value} != ${coinvalue.toString}------recver=${this.nodeName}")
            this.alreadyDecide = coinvalue
            val est = coinvalue
            ba ! new RepeatBinaryAgreement(this.nodeName,this.round,this.epoch+1,est)
          }else if(alreadyDecide == coinvalue){
            println(s"==========BA resut ba,return caller,source=${coin.source},value != coinvalue.toString:${value} != ${coinvalue.toString}------recver=${this.nodeName}")
             this.caller ! ResultOfBA(this.round,this.epoch,alreadyDecide)
          }
        }else{
          println(s"==========BA resut ba,source=${coin.source},value != coinvalue.toString:${value} != ${coinvalue.toString}------recver=${this.nodeName}")
          val est = value.toInt
          ba ! new RepeatBinaryAgreement(this.nodeName,this.round,this.epoch+1,est)
        }
      }else{
        println(s"==========BA resut ba,value exception,source=${coin.source},value != coinvalue.toString:${value} != ${coinvalue.toString}------recver=${this.nodeName}")
        val est = coinvalue
        ba ! new RepeatBinaryAgreement(this.nodeName,this.round,this.epoch+1,est)
      }
    }
  }

  private def getData(round:String,epoch:Int):BAData={
    val key = round+"-"+epoch.toString
    if(this.data.contains(key)){
      this.data(key)
    }else{
      val d = new BAData
      this.data += key -> d
      d
    }
  }
}
