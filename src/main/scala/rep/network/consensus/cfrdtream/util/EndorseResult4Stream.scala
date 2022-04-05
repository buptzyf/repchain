package rep.network.consensus.cfrdtream.util

import rep.proto.rc2.{Signature}

class EndorseResult4Stream {
  var endorseName:String = ""
  var endorseAddr:String = ""
  var hashOfBlock:String = ""
  var sign:Signature = null
  var isVerify : Boolean = false

  def set(endorseName:String,endorseAddr:String,hashOfBlock:String,sign:Signature): Unit ={
    this.endorseAddr = endorseAddr
    this.endorseName = endorseName
    this.hashOfBlock = hashOfBlock
    this.sign = sign
    this.isVerify = false
  }

}
