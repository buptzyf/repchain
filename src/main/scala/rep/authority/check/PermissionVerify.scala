package rep.authority.check

import java.util.concurrent.ConcurrentHashMap

import rep.app.conf.SystemProfile
import rep.authority.cache.authbind.ImpAuthBindToCert
import rep.authority.cache.authcache.ImpAuthorizeCache
import rep.authority.cache.opcache.ImpOperateCache
import rep.authority.cache.signercache.ISignerCache.signerData
import rep.authority.cache.signercache.ImpSignerCache
import rep.crypto.cert.certCache
import rep.log.RepLogger
import rep.protos.peer.CertId
import rep.protos.peer.Transaction.Type
import rep.sc.Sandbox.SandboxException
import rep.sc.SandboxDispatcher._
import rep.sc.Shim
import rep.storage.ImpDataPreload
import rep.utils.IdTool

import scala.util.control.Breaks.{break, breakable}

/**
 * Created by jiangbuyun on 2020/06/24.
 * 实现权限校验
 */

class PermissionVerify(sysTag:String) {
  val opcache = ImpOperateCache.GetOperateCache(this.sysTag)
  val authcache = ImpAuthorizeCache.GetAuthorizeCache(this.sysTag)
  val sigcache = ImpSignerCache.GetSignerCache(this.sysTag)
  val bind = ImpAuthBindToCert.GetAuthBindToCertCache(this.sysTag)

  //合约权限校验
  //dbinstance没有的情况，参数的值为null
  //did，opname必填
  def CheckPermission(did:String,certName:String,opname:String,dbinstance:ImpDataPreload): Boolean ={
    var r = false
    RepLogger.Permission_Logger.trace(s"System=${this.sysTag},PermissionVerify.CheckPermission entry check,did=${did},opname=${opname}")
    if(!IsChainCert(did)) {
      RepLogger.Permission_Logger.trace(s"System=${this.sysTag},PermissionVerify.CheckPermission is not chain cert,did=${did},opname=${opname}")
      //不是链证书，检查是否有合约部署权限
      //获取Signer信息
      val od = opcache.getOperateData(opname, dbinstance)
      val sd = sigcache.getSignerData(did,dbinstance)
      if(sd == null){
        //实体账户不存在
        RepLogger.Permission_Logger.trace(s"System=${this.sysTag},PermissionVerify.CheckPermission signer is not exist,did=${did},opname=${opname}")
        throw new SandboxException(ERR_NO_OPERATE)
      }else if(sd.signer_valid){
        //实体账户有效
        RepLogger.Permission_Logger.trace(s"System=${this.sysTag},PermissionVerify.CheckPermission signer valid,did=${did},opname=${opname}")
        if (od == null) {
          //操作不存在
          RepLogger.Permission_Logger.trace(s"System=${this.sysTag},PermissionVerify.CheckPermission op is not found,did=${did},opname=${opname}")
          throw new SandboxException(ERR_NO_OPERATE)
        } else if (od.opValid) {
          //操作有效
          RepLogger.Permission_Logger.trace(s"System=${this.sysTag},PermissionVerify.CheckPermission op valid,did=${did},opname=${opname}")
          if (od.isOpen) {
            //属于开放操作
            RepLogger.Permission_Logger.trace(s"System=${this.sysTag},PermissionVerify.CheckPermission op is open,did=${did},opname=${opname}")
            r = true
          } else {
            if (od.register.equals(did)) {
              //属于自己注册的操作
              RepLogger.Permission_Logger.trace(s"System=${this.sysTag},PermissionVerify.CheckPermission op owner,did=${did},opname=${opname}")
              r = true
            } else {
              //不属于自己的操作，检查是否有授权
              RepLogger.Permission_Logger.trace(s"System=${this.sysTag},PermissionVerify.CheckPermission check auth,did=${did},opname=${opname}")
              if(sd.opids.containsKey(od.opId)){
                val opid = sd.opids.get(od.opId)
                if(opid != null){
                  if(opid.length > 0){
                    breakable(opid.foreach(f=>{
                      val ad = authcache.getAuthorizeData(f,dbinstance)
                      if(ad != null){
                        if(ad.authorizeValid){
                          val b = IsBindCert(ad.authid,sd,dbinstance)
                          if(b._1){
                            if(b._2.equals(did+"."+certName)){
                              r = true
                              break
                            }
                          }else{
                            r = true
                            break
                          }
                        }
                      }else{
                        //授权已经不存在
                        RepLogger.Permission_Logger.trace(s"System=${this.sysTag},PermissionVerify.CheckPermission auth is not exist,did=${did},opname=${opname}")
                        throw new SandboxException(ERR_NO_AUTHORIZE)
                      }
                    }))
                    if(!r){
                      RepLogger.Permission_Logger.trace(s"System=${this.sysTag},PermissionVerify.CheckPermission auth invalid,did=${did},opname=${opname}")
                      throw new SandboxException(ERR_INVALID_AUTHORIZE)
                    }
                  }else{
                    //没有找到授权的操作
                    RepLogger.Permission_Logger.trace(s"System=${this.sysTag},PermissionVerify.CheckPermission not found auth,did=${did},opname=${opname}")
                    throw new SandboxException(ERR_NO_OP_IN_AUTHORIZE)
                  }
                }else{
                  //没有找到授权的操作
                  RepLogger.Permission_Logger.trace(s"System=${this.sysTag},PermissionVerify.CheckPermission not found auth,did=${did},opname=${opname}")
                  throw new SandboxException(ERR_NO_OP_IN_AUTHORIZE)
                }
              }else{
                //没有该操作的授权
                RepLogger.Permission_Logger.trace(s"System=${this.sysTag},PermissionVerify.CheckPermission not found auth,did=${did},opname=${opname}")
                throw new SandboxException(ERR_NO_OP_IN_AUTHORIZE)
              }
            }
          }
        } else {
          //操作无效
          RepLogger.Permission_Logger.trace(s"System=${this.sysTag},PermissionVerify.CheckPermission op invalid,did=${did},opname=${opname}")
          throw new SandboxException(ERR_INVALID_OPERATE)
        }
      }else{
        //实体账户无效
        RepLogger.Permission_Logger.trace(s"System=${this.sysTag},PermissionVerify.CheckPermission signer invalid,did=${did},opname=${opname}")
        throw new SandboxException(ERR_INVALID_SIGNER)
      }
    }else{
      r = true
      RepLogger.Permission_Logger.trace(s"System=${this.sysTag},PermissionVerify.CheckPermission is chain cert,did=${did},opname=${opname}")
    }
    r
  }

  //通过证书的Id对象来校验权限
  def CheckPermissionOfCertId(certid:CertId, opname:String, dbinstance:ImpDataPreload): Boolean ={
    CheckPermission(certid.creditCode,certid.certName,opname,dbinstance)
  }

  //通过证书Id的字符串来校验权限
  def CheckPermissionOfCertIdStr(certid:String, opname:String, dbinstance:ImpDataPreload): Boolean ={
    CheckPermissionOfCertId(IdTool.getCertIdFromName(certid), opname, dbinstance)
  }

  //通过证书的hash来校验权限
  def CheckPermissionOfCertHash(certhash:String, opname:String, dbinstance:ImpDataPreload): Boolean ={
    CheckPermissionOfCertIdStr(certCache.getCertIdForHash(certhash,sysTag,dbinstance), opname, dbinstance)
  }

  def CheckPermissionOfDeployContract(dotrans: DoTransactionOfSandboxInSingle,shim:Shim):Boolean={
    var r = true
    val cid = dotrans.t.cid.get

    if(dotrans.t.`type` == Type.CHAINCODE_DEPLOY && dotrans.t.cid.get.chaincodeName == "ContractAssetsTPL"){
      System.out.println("")
    }
    try {
      if (!CheckPermissionOfCertId(dotrans.t.signature.get.certId.get, opname = "*.deploy", shim.sr)) {
        r = CheckPermissionOfCertId(dotrans.t.signature.get.certId.get, cid.chaincodeName + ".deploy", shim.sr)
      }
    } catch {
      case e: SandboxException =>
        r = CheckPermissionOfCertId(dotrans.t.signature.get.certId.get, cid.chaincodeName + ".deploy", shim.sr)
    }

    r
  }

  def CheckPermissionOfSetStateContract(dotrans: DoTransactionOfSandboxInSingle,shim:Shim)={
    var r = true
    val cid = dotrans.t.cid.get
    try {
      if (!CheckPermissionOfCertId(dotrans.t.signature.get.certId.get, opname = "*.setState", shim.sr)) {
        r = CheckPermissionOfCertId(dotrans.t.signature.get.certId.get, cid.chaincodeName + ".setState", shim.sr)
      }
    } catch {
      case e: SandboxException =>
        r = CheckPermissionOfCertId(dotrans.t.signature.get.certId.get, cid.chaincodeName + ".setState", shim.sr)
    }

    r
  }

  def CheckPermissionOfInvokeContract(dotrans: DoTransactionOfSandboxInSingle,shim:Shim)={
    val cid = dotrans.t.cid.get
    if( dotrans.t.getIpt.function == "grantOperate"){
      System.out.println("")
    }
    CheckPermissionOfCertId(dotrans.t.signature.get.certId.get,
                                cid.chaincodeName+"."+dotrans.t.getIpt.function,
                                shim.sr)
  }

  private def IsChainCert(did:String):Boolean={
    var r = true
    val cid = IdTool.getCertIdFromName(SystemProfile.getChainCertName)
    if(!cid.creditCode.equals(did)){
      r = false
    }
    r
  }

  private def IsBindCert(authid:String,sd:signerData,dbinstance:ImpDataPreload):(Boolean,String)={
    var r = (false,"")
    if(sd != null && !sd.certNames.isEmpty){
      breakable(sd.certNames.foreach(f=>{
        val b = bind.hasAuthBindToCert(authid,f,dbinstance)
        if(b){
          r = (true,f)
          break
        }
      }))
    }
    r
  }

}

object PermissionVerify {
  import scala.collection.JavaConverters._
  private implicit var singleobjs = new ConcurrentHashMap[String, PermissionVerify]() asScala
  /**
   * @author jiangbuyun
   * @version	1.1
   * @since	2020-06-25
   * @category	根据系统名称获取权限验证类
   * @param	SystemName String 系统名称
   * @return	如果成功返回PermissionVerify实例，否则为null
   */
  def GetPermissionVerify(SystemName: String): PermissionVerify = {
    var singleobj: PermissionVerify = null
    synchronized {
      if (singleobjs.contains(SystemName)) {
        singleobj = singleobjs.get(SystemName).getOrElse(null)
      } else {
        singleobj = new PermissionVerify(SystemName)
        singleobjs.put(SystemName, singleobj)
      }
      singleobj
    }
  }
}