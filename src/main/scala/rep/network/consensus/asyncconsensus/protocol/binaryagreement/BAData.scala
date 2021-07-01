package rep.network.consensus.asyncconsensus.protocol.binaryagreement

import scala.collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer

class BAData {
  private var est_value : HashMap[Int,ArrayBuffer[String]] = new HashMap[Int,ArrayBuffer[String]]()
  private var aux_value : HashMap[Int,ArrayBuffer[String]] = new HashMap[Int,ArrayBuffer[String]]()
  private var conf_value:HashMap[String,ArrayBuffer[String]] = new HashMap[String,ArrayBuffer[String]]()//key=String example:0,1,0_1

  private var est_sent : HashMap[Int,Boolean] = new HashMap[Int,Boolean]() // key example:0,1
  private var conf_sent: HashMap[String,Boolean] = new HashMap[String,Boolean]() //key=String example:0,1,0_1
  private var bin_value: ArrayBuffer[Int] = new ArrayBuffer[Int]()

  private var conf_value_out:String = ""

  def setConfValueOut(value:String):Unit={
    this.conf_value_out = value
  }

  def getConfValueOut:String={
    this.conf_value_out
  }

  def saveEstSender(value:Int,sendName:String): Unit ={
    if(value == 0 || value == 1){
      val names = checkArray(est_value.getOrElse(value,null))
      if(!names.contains(sendName)){
        names += sendName
        est_value += value -> names
      }
    }
  }

  def isRecvEstSender(value:Int,sendName:String):Boolean={
    if(value == 0 || value == 1){
      if(!est_value.contains(value)){
        false
      }else{
        val names = est_value(value)
        if(names == null){
          false
        }else{
          if(names.contains(sendName)){
            true
          }else{
            false
          }
        }
      }
    }else{
      false
    }
  }

  def getEstCount(value:Int):Int={
    if(value == 0 || value == 1){
      val names = checkArray(est_value.getOrElse(value,null))
      if(names == null){
        0
      }else{
        names.length
      }
    }else{
      0
    }
  }

  def getAuxCount(value:Int):Int={
    if(value == 0 || value == 1){
      val names = checkArray(aux_value.getOrElse(value,null))
      if(names == null){
        0
      }else{
        names.length
      }
    }else{
      0
    }
  }

  def getConfCount(value:String):Int={
    if(value == "0" || value == "1" || value == "0_1"){
      val names = checkArray(conf_value.getOrElse(value,null))
      if(names == null){
        0
      }else{
        names.length
      }
    }else{
      0
    }
  }

  def saveAuxSender(value:Int,sendName:String): Unit ={
    if(value == 0 || value == 1){
      val names = checkArray(aux_value.getOrElse(value,null))
      if(!names.contains(sendName)){
        names += sendName
        aux_value += value -> names
      }
    }
  }

  def isRecvAuxSender(value:Int,sendName:String):Boolean={
    if(value == 0 || value == 1){
      if(!aux_value.contains(value)){
        false
      }else{
        val names = aux_value(value)
        if(names == null){
          false
        }else{
          if(names.contains(sendName)){
            true
          }else{
            false
          }
        }
      }
    }else{
      false
    }
  }

  def saveConfSender(value:String,sendName:String): Unit ={
    if(value == "0" || value == "1" || value == "0_1"){
      val names = checkArray(conf_value.getOrElse(value,null))
      if(!names.contains(sendName)){
        names += sendName
        conf_value += value -> names
      }
    }
  }

  def isRecvConfValue(value:String,sendName:String):Boolean={
    if(value == "0" || value == "1" || value == "0_1"){
      if(!conf_value.contains(value)){
        false
      }else{
        val names = conf_value(value)
        if(names == null){
          false
        }else{
          if(names.contains(sendName)){
            true
          }else{
            false
          }
        }
      }
    }else{
      false
    }
  }

  private def checkArray(buf:ArrayBuffer[String]):ArrayBuffer[String]={
    var names : ArrayBuffer[String] = buf
    if(buf == null){
      names = new ArrayBuffer[String]()
    }
    names
  }

  def setEstSent(value:Int):Unit={
    if(value == 0 || value == 1)
      est_sent += value -> true
  }

  def isSentEst(value:Int): Boolean ={
    if(est_sent.contains(value)){
      est_sent(value)
    }else{
      false
    }
  }

  def setConfSent(value:String): Unit ={
    if(value == "0" || value == "1" || value == "0_1")
    conf_sent += value -> true
  }

  def isSentConf(value:String):Boolean={
    if(conf_sent.contains(value)){
      conf_sent(value)
    }else{
      false
    }
  }

  def addBinValue(value:Int):Unit={
    if(value == 0 || value == 1){
      if(!bin_value.contains(value)){
        bin_value += value
      }
    }
  }

  def getBinValueForFirst:Int={
    bin_value(0)
  }

  def hasBinValue:Boolean={
    if(bin_value.length >0){
      true
    }else{
      false
    }
  }

  def containsInBinValue(value:Int):Boolean={
    bin_value.contains(value)
  }


}
