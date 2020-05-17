package rep.authority.cache

import rep.storage._
import rep.app.conf.SystemProfile
import rep.protos.peer.{Authorize, Certificate, Operate, Signer, Transaction, BindCertToAuthorize}
import rep.storage.IdxPrefix.WorldStateKeyPreFix
import rep.utils.{IdTool, SerializeUtils}

class AccountMgr(sysTag:String) {
  val perfix = IdxPrefix.WorldStateKeyPreFix + SystemProfile.getAccountChaincodeName + "_"
  val sr: ImpDataAccess = ImpDataAccess.GetDataAccess(sysTag)

  //通过证书hash值获取证书对象
  def getCertInfoByCertHash(hash256:String):Certificate={
    getCertInfoByCertId(getCertName(hash256))
  }

  //通过证书id字符串获取证书对象
  def getCertInfoByCertId(certid:String): Certificate ={
    var result : Certificate = null
    if(certid != null && !certid.equalsIgnoreCase("")){
      try{
        var tmp = Option(sr.Get(perfix+certid))
        if (tmp != None && !(new String(tmp.get)).equalsIgnoreCase("null")) {
          result = SerializeUtils.deserialise(tmp.get).asInstanceOf[Certificate]
        }
      }catch{
        case e: Exception => {
          //todo add exception to log file
          result = null
        }
      }
    }
    result
  }

  //通过操作id获取操作对象
  def getOperateByOpid(opid:String):Operate = {
    var result : Operate = null
    if(opid != null && !opid.equalsIgnoreCase("")){
      try{
        var tmp = Option(sr.Get(perfix+opid))
        if (tmp != None && !(new String(tmp.get)).equalsIgnoreCase("null")) {
          result = SerializeUtils.deserialise(tmp.get).asInstanceOf[Operate]
        }
      }catch{
        case e: Exception => {
          //todo add exception to log file
          result = null
        }
      }
    }
    result
  }

  //通过交易对象获取合约的有效性
  def getContractStateByTrans(t:Transaction):Boolean = {
    var result = false
    if(t != null){
      try{
        val txcid = IdTool.getTXCId(t)
        val key_tx_state = WorldStateKeyPreFix + txcid + "_STATE"
        val state_bytes = Option(sr.Get(key_tx_state))
        //合约不存在
        if (state_bytes  != None ) {
          result = false
        }else{
          result = SerializeUtils.deserialise(state_bytes.get).asInstanceOf[Boolean]
        }
      }catch{
        case e : Exception =>{
          //todo add exception to log file
          result = false
        }
      }

    }
    result
  }

  //通过证书id字符串获取签名者
  def getSignerByCertId(certid:String):Signer = {
    var result : Signer = null
    if(certid != null){
      val Certid = IdTool.getCertIdFromName(certid)
      if(Certid != null){
        try{
          var tmp = Option(sr.Get(perfix+Certid.creditCode))
          if (tmp != None && !(new String(tmp.get)).equalsIgnoreCase("null")) {
            result = SerializeUtils.deserialise(tmp.get).asInstanceOf[Signer]
          }
        }catch{
          case e: Exception => {
            //todo add exception to log file
            result = null
          }
        }
      }
    }
    result
  }

  //通过证书的hash值获取签名者
  def getSignerByCertHash(hash256:String):Signer = {
    getSignerByCertId(getCertName(hash256))
  }

  //通过authid获取授权对象
  def getAuthorizeByAuthId(authid:String):Authorize = {
    var result : Authorize = null
    if(authid != null){
        try{
          var tmp = Option(sr.Get(perfix+authid))
          if (tmp != None && !(new String(tmp.get)).equalsIgnoreCase("null")) {
            result = SerializeUtils.deserialise(tmp.get).asInstanceOf[Authorize]
          }
        }catch{
          case e: Exception => {
            //todo add exception to log file
            result = null
          }
        }
    }
    result
  }

  //通过授权id和身份标识获取绑定到授权id的证书信息
  def getBindCertToAuthorizeByAuthidAndCredit(authid:String,creditcode:String):BindCertToAuthorize={
    var result : BindCertToAuthorize = null
    if(authid != null && creditcode != null){
      try{
        var tmp = Option(sr.Get(perfix+authid+"_"+creditcode))
        if (tmp != None && !(new String(tmp.get)).equalsIgnoreCase("null")) {
          result = SerializeUtils.deserialise(tmp.get).asInstanceOf[BindCertToAuthorize]
        }
      }catch{
        case e: Exception => {
          //todo add exception to log file
          result = null
        }
      }
    }
    result
  }

  //通过证书hash值获取证书id
  private def getCertName(hash256:String):String={
    var result = ""
    try{
      if(hash256 != null){
        var tmp = Option(sr.Get(perfix+hash256))
        if (tmp != None && !(new String(tmp.get)).equalsIgnoreCase("null")) {
          result = new String(tmp.get)
        }
      }
    }catch{
      case e: Exception => {
        //todo add exception to log file
        result = ""
      }
    }
    result
  }

}
