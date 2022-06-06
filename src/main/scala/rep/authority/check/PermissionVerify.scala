package rep.authority.check


import rep.app.system.RepChainSystemContext
import rep.authority.cache.SignerCache.signerData
import rep.authority.cache.{AuthenticateBindToCertCache, AuthenticateCache, CertificateCache, CertificateHashCache, OperateCache, PermissionCacheManager, SignerCache}
import rep.log.RepLogger
import rep.proto.rc2.CertId
import rep.sc.Sandbox.SandboxException
import rep.sc.SandboxDispatcher._
import rep.sc.tpl.did.DidTplPrefix
import rep.storage.chain.preload.BlockPreload
import rep.utils.IdTool

import scala.util.control.Breaks.{break, breakable}

/**
 * Created by jiangbuyun on 2020/06/24.
 * 实现权限校验
 */

class PermissionVerify(ctx: RepChainSystemContext) {
  val opCache = ctx.getPermissionCacheManager.getCache(DidTplPrefix.operPrefix).asInstanceOf[OperateCache]
  val authCache = ctx.getPermissionCacheManager.getCache(DidTplPrefix.authPrefix).asInstanceOf[AuthenticateCache]
  val sigCache = ctx.getPermissionCacheManager.getCache(DidTplPrefix.signerPrefix).asInstanceOf[SignerCache]
  val bind = ctx.getPermissionCacheManager.getCache(DidTplPrefix.bindPrefix).asInstanceOf[AuthenticateBindToCertCache]
  val certCache = ctx.getPermissionCacheManager.getCache(DidTplPrefix.certPrefix).asInstanceOf[CertificateCache]
  val certHashCache = ctx.getPermissionCacheManager.getCache(DidTplPrefix.hashPrefix).asInstanceOf[CertificateHashCache]

  //合约权限校验
  //dbinstance没有的情况，参数的值为null
  //did，opname必填
  def CheckPermission(did: String, certName: String, opname: String, dbinstance: BlockPreload): Boolean = {
    var r = false
    RepLogger.Permission_Logger.trace(s"System=${ctx.getSystemName},PermissionVerify.CheckPermission entry check,did=${did},opname=${opname}")
    if (!IsChainCert(did)) {
      RepLogger.Permission_Logger.trace(s"System=${ctx.getSystemName},PermissionVerify.CheckPermission is not chain cert,did=${did},opname=${opname}")
      //不是链证书，检查是否有合约部署权限
      //获取Signer信息
      val od = opCache.get(opname, dbinstance)
      val sd = sigCache.get(did, dbinstance)
      if (sd == None) {
        //实体账户不存在
        RepLogger.Permission_Logger.trace(s"System=${ctx.getSystemName},PermissionVerify.CheckPermission signer is not exist,did=${did},opname=${opname}")
        throw new SandboxException(ERR_NO_OPERATE)
      } else if (sd.get.signer_valid) {
        //实体账户有效
        RepLogger.Permission_Logger.trace(s"System=${ctx.getSystemName},PermissionVerify.CheckPermission signer valid,did=${did},opname=${opname}")
        if (od == None) {
          //操作不存在
          RepLogger.Permission_Logger.trace(s"System=${ctx.getSystemName},PermissionVerify.CheckPermission op is not found,did=${did},opname=${opname}")
          r = false
          throw new SandboxException(ERR_NO_OPERATE)
        } else if (od.get.opValid) {
          //操作有效
          RepLogger.Permission_Logger.trace(s"System=${ctx.getSystemName},PermissionVerify.CheckPermission op valid,did=${did},opname=${opname}")
          if (od.get.isOpen) {
            //属于开放操作
            RepLogger.Permission_Logger.trace(s"System=${ctx.getSystemName},PermissionVerify.CheckPermission op is open,did=${did},opname=${opname}")
            r = true
          } else {
            if (od.get.register.equals(did)) {
              //属于自己注册的操作
              RepLogger.Permission_Logger.trace(s"System=${ctx.getSystemName},PermissionVerify.CheckPermission op owner,did=${did},opname=${opname}")
              r = true
            } else {
              //不属于自己的操作，检查是否有授权
              RepLogger.Permission_Logger.trace(s"System=${ctx.getSystemName},PermissionVerify.CheckPermission check auth,did=${did},opname=${opname}")
              if (sd.get.opIds.containsKey(od.get.opId)) {
                val opid = sd.get.opIds.get(od.get.opId)
                if (opid != null) {
                  if (opid.length > 0) {
                    breakable(opid.foreach(f => {
                      val ad = authCache.get(f, dbinstance)
                      if (ad != None) {
                        if (ad.get.authorizeValid) {
                          val b = IsBindCert(ad.get.authid, sd.get, dbinstance)
                          if (b._1) {
                            if (b._2.equals(did + "." + certName)) {
                              r = true
                              break
                            }
                          } else {
                            r = true
                            break
                          }
                        }
                      } else {
                        //授权已经不存在
                        RepLogger.Permission_Logger.trace(s"System=${ctx.getSystemName},PermissionVerify.CheckPermission auth is not exist,did=${did},opname=${opname}")
                        throw new SandboxException(ERR_NO_AUTHORIZE)
                      }
                    }))
                    if (!r) {
                      RepLogger.Permission_Logger.trace(s"System=${ctx.getSystemName},PermissionVerify.CheckPermission auth invalid,did=${did},opname=${opname}")
                      throw new SandboxException(ERR_INVALID_AUTHORIZE)
                    }
                  } else {
                    //没有找到授权的操作
                    RepLogger.Permission_Logger.trace(s"System=${ctx.getSystemName},PermissionVerify.CheckPermission not found auth,did=${did},opname=${opname}")
                    throw new SandboxException(ERR_NO_OP_IN_AUTHORIZE)
                  }
                } else {
                  //没有找到授权的操作
                  RepLogger.Permission_Logger.trace(s"System=${ctx.getSystemName},PermissionVerify.CheckPermission not found auth,did=${did},opname=${opname}")
                  throw new SandboxException(ERR_NO_OP_IN_AUTHORIZE)
                }
              } else {
                //没有该操作的授权
                RepLogger.Permission_Logger.trace(s"System=${ctx.getSystemName},PermissionVerify.CheckPermission not found auth,did=${did},opname=${opname}")
                throw new SandboxException(ERR_NO_OP_IN_AUTHORIZE)
              }
            }
          }
        } else {
          //操作无效
          RepLogger.Permission_Logger.trace(s"System=${ctx.getSystemName},PermissionVerify.CheckPermission op invalid,did=${did},opname=${opname}")
          throw new SandboxException(ERR_INVALID_OPERATE)
        }
      } else {
        //实体账户无效
        RepLogger.Permission_Logger.trace(s"System=${ctx.getSystemName},PermissionVerify.CheckPermission signer invalid,did=${did},opname=${opname}")
        throw new SandboxException(ERR_INVALID_SIGNER)
      }
    } else {
      r = true
      RepLogger.Permission_Logger.trace(s"System=${ctx.getSystemName},PermissionVerify.CheckPermission is chain cert,did=${did},opname=${opname}")
    }
    r
  }

  //通过证书的Id对象来校验权限
  def CheckPermissionOfCertId(certId: CertId, opName: String, dbInstance: BlockPreload): Boolean = {
    CheckPermission(certId.creditCode, certId.certName, opName, dbInstance)
  }

  //通过证书Id的字符串来校验权限
  def CheckPermissionOfCertIdStr(certId: String, opName: String, dbInstance: BlockPreload): Boolean = {
    CheckPermissionOfCertId(IdTool.getCertIdFromName(certId), opName, dbInstance)
  }

  //通过证书的hash来校验权限
  def CheckPermissionOfCertHash(certHash: String, opName: String, dbInstance: BlockPreload): Boolean = {
    val certId = certHashCache.get(certHash, dbInstance)
    if (certId == None) {
      RepLogger.Permission_Logger.trace(s"System=${ctx.getSystemName},PermissionVerify.CheckPermission certHash not exist,certHash=${certHash},opName=${opName}")
      false
    } else {
      CheckPermissionOfCertIdStr(certId.get, opName, dbInstance)
    }
  }

  def CheckPermissionOfDeployContract(doTrans: DoTransactionOfSandboxInSingle): Boolean = {
    var r = true
    val cid = doTrans.t.cid.get

    val dbInstance = ctx.getBlockPreload(doTrans.da)
    /*if(doTrans.t.`type` == Type.CHAINCODE_DEPLOY && doTrans.t.cid.get.chaincodeName == "ContractAssetsTPL"){
      System.out.println("")
    }*/
    try {
      if (!CheckPermissionOfCertId(doTrans.t.signature.get.certId.get, opName = "*.deploy", dbInstance)) {
        r = CheckPermissionOfCertId(doTrans.t.signature.get.certId.get, cid.chaincodeName + ".deploy", dbInstance)
      }
    } catch {
      case e: SandboxException =>
        r = CheckPermissionOfCertId(doTrans.t.signature.get.certId.get, cid.chaincodeName + ".deploy", dbInstance)
    }

    r
  }

  def CheckPermissionOfSetStateContract(doTrans: DoTransactionOfSandboxInSingle) = {
    var r = true
    val cid = doTrans.t.cid.get
    val dbInstance = ctx.getBlockPreload(doTrans.da)
    try {
      if (!CheckPermissionOfCertId(doTrans.t.signature.get.certId.get, opName = "*.setState", dbInstance)) {
        r = CheckPermissionOfCertId(doTrans.t.signature.get.certId.get, cid.chaincodeName + ".setState", dbInstance)
      }
    } catch {
      case e: SandboxException =>
        r = CheckPermissionOfCertId(doTrans.t.signature.get.certId.get, cid.chaincodeName + ".setState", dbInstance)
    }

    r
  }

  def CheckPermissionOfInvokeContract(doTrans: DoTransactionOfSandboxInSingle) = {
    val cid = doTrans.t.cid.get
    if (doTrans.t.getIpt.function == "grantOperate") {
      System.out.println("")
    }
    val dbInstance = ctx.getBlockPreload(doTrans.da)
    CheckPermissionOfCertId(doTrans.t.signature.get.certId.get,
      cid.chaincodeName + "." + doTrans.t.getIpt.function,
      dbInstance)
  }

  private def IsChainCert(did: String): Boolean = {
    var r = true
    val cid = IdTool.getCertIdFromName(ctx.getConfig.getChainCertName)
    if (!cid.creditCode.equals(did)) {
      r = false
    }
    r
  }

  private def IsBindCert(authid: String, sd: signerData, dbInstance: BlockPreload): (Boolean, String) = {
    var r = (false, "")
    if (sd != null && !sd.certNames.isEmpty) {
      breakable(sd.certNames.foreach(f => {
        val b = bind.get(authid, f, dbInstance)
        if (b != None && b.get) {
          r = (true, f)
          break
        }
      }))
    }
    r
  }
}