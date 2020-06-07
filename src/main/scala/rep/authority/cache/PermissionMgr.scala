package rep.authority.cache

import rep.app.conf.SystemProfile
import rep.protos.peer.{Authorize, CertId, Operate, Signer}
import rep.storage.{IDataAccess, IdxPrefix}
import rep.utils.SerializeUtils

import scala.collection.mutable.ArrayBuffer

class PermissionMgr(sr:IDataAccess) {
  val perfix = IdxPrefix.WorldStateKeyPreFix + SystemProfile.getAccountChaincodeName + "_"

  //通过证书id字符串获取签名者
  def getSignerByCertId(certid:CertId):Signer = {
    var result : Signer = null
    if(certid != null){
      try{
        var tmp = Option(sr.Get(perfix+certid.creditCode))
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
    result
  }

  private def getOperateByOpid(opid:String):Operate = {
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

  //通过authid获取授权对象
  private def getAuthorizeByAuthId(authid:String):Authorize = {
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

  //查找效率低，需要改进
  private def isBindCertIdToAuthorize(authid:String,certid:CertId): Boolean ={
    var r = false
    if(authid != null && certid != null){
      try{
        var tmp = Option(sr.Get(perfix+authid+"_"+certid.creditCode))
        if (tmp != None && !(new String(tmp.get)).equalsIgnoreCase("null")) {
          r = true
        }
      }catch{
        case e: Exception => {
          //todo add exception to log file
          r = false
        }
      }
    }
    r
  }

  private def getAllAuthOfSignerOnCert(signer: Signer,certId: CertId):Array[String]={
    var oas = new ArrayBuffer[String]()
    if(!signer.authorizeIds.isEmpty){
      //签名者拥有自有的操作
      signer.authorizeIds.foreach(oaid=>{
        val oa = getAuthorizeByAuthId(oaid)
        if(oa != null){
          if(oa.authorizeValid){
            //授权有效
            oas ++= oa.opId.toArray
          }
        }
      })
    }
    oas.distinct.toArray
  }

  //获取所有开放的操作，开放操作是不需要授权的操作
  private def getAllOpenOperate:Array[String]={
    var openops = new ArrayBuffer[String]
    openops.toArray
  }

  def getAllOperateOfSigner(signer: Signer,certId: CertId):Array[Operate]={
    var ops = new ArrayBuffer[Operate]()
    var opids = getAllAuthOfSignerOnCert(signer,certId)
    if(!signer.operateIds.isEmpty){
      //签名者拥有自有的操作
      opids = opids ++ signer.operateIds.toArray
      opids = opids.distinct
    }
    var tmp = getAllOpenOperate
    if(tmp.length > 0){
      opids = opids ++ tmp
      opids = opids.distinct
    }

    opids.foreach(opid=>{
      val op = getOperateByOpid(opid)
      if(op != null && op.opValid){
        ops += op
      }
    })
    ops.toArray
  }


}
