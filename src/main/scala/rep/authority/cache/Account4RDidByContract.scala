package rep.authority.cache

import rep.app.conf.SystemProfile
import rep.protos.peer.{ Operate}
import rep.sc.Sandbox.SandboxException
import rep.sc.SandboxDispatcher.{DoTransactionOfSandbox,
        ERR_Signer_INVAILD, ERR_NO_FOUND_Signer,ERR_NO_PERMISSION_OF_DEPLOY,
        ERR_NO_PERMISSION_OF_SETSTATE,ERR_NO_PERMISSION_OF_INVOKE}
import rep.sc.Shim
import rep.storage.{ ImpDataPreload,IDataAccess}
import rep.utils.{IdTool}


class Account4RDidByContract(shim:Shim) {
  val sr: ImpDataPreload = shim.sr

  private def CheckOps(dotrans: DoTransactionOfSandbox): Array[Operate] ={
    val pmgr = new PermissionMgr(sr.asInstanceOf[IDataAccess])
    val certid_ts = dotrans.t.signature.get.certId

    //不是链证书，检查是否有合约部署权限
    //获取Signer信息
    val signer = pmgr.getSignerByCertId(certid_ts.get)
    if(signer == None){
      throw new SandboxException(ERR_NO_FOUND_Signer)
    }else{
      if(!signer.signerValid){
        throw new SandboxException(ERR_Signer_INVAILD)
      }
      pmgr.getAllOperateOfSigner(signer,certid_ts.get)
    }
  }

  def hasPermissionOfDeployContract(dotrans: DoTransactionOfSandbox)={
    val certid_ts = dotrans.t.signature.get.certId
    if(!IdTool.getSigner4String(certid_ts.get).equals(SystemProfile.getChainCertName)){
      //不是链证书，检查是否有合约部署权限
      //获取Signer信息
      val ops = CheckOps(dotrans)
      val cid = dotrans.t.cid.get
      val tmpops = ops.filter(_.authFullName.equals("*.deploy"))
      val tmpops1 = ops.filter(_.authFullName.equals(cid.chaincodeName+".deploy"))

      if(tmpops.length<=0 && tmpops1.length <= 0){
        throw new SandboxException(ERR_NO_PERMISSION_OF_DEPLOY)
      }
    }
  }

  def hasPermissionOfSetStateContract(dotrans: DoTransactionOfSandbox)={
    val certid_ts = dotrans.t.signature.get.certId
    if(!IdTool.getSigner4String(certid_ts.get).equals(SystemProfile.getChainCertName)){
      //不是链证书，检查是否有合约部署权限
      //获取Signer信息
      val ops = CheckOps(dotrans)
      val cid = dotrans.t.cid.get
      val tmpops = ops.filter(_.authFullName.equals("*.setState"))
      val tmpops1 = ops.filter(_.authFullName.equals(cid.chaincodeName+".setState"))

      if(tmpops.length<=0 && tmpops1.length <= 0){
        throw new SandboxException(ERR_NO_PERMISSION_OF_SETSTATE)
      }
    }
  }

  def hasPermissionOfInvokeContract(dotrans: DoTransactionOfSandbox)={
    val certid_ts = dotrans.t.signature.get.certId
    if(!IdTool.getSigner4String(certid_ts.get).equals(SystemProfile.getChainCertName)){
      //不是链证书，检查是否有合约部署权限
      //获取Signer信息
      val ops = CheckOps(dotrans)
      val cid = dotrans.t.cid.get
      val tmpops = ops.filter(_.authFullName.equals(cid.chaincodeName+"."+dotrans.t.getIpt.function))

      if(tmpops.length<=0){
        throw new SandboxException(ERR_NO_PERMISSION_OF_INVOKE)
      }
    }
  }
}
