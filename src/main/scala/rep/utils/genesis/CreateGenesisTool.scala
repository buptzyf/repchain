package rep.utils.genesis

import java.io.File
import com.typesafe.config.{Config, ConfigFactory}
import rep.proto.rc2.Authorize.TransferType
import rep.proto.rc2.ChaincodeId
import rep.proto.rc2.Operate.OperateType
import scala.collection.mutable.ArrayBuffer

class CreateGenesisTool(file:File)  {
  private var builder = new GenesisBuilder
  private val conf : Config = ConfigFactory.parseFile(file)

  def buildGenesisFile(fileName:String):Boolean={
    var r = false
    try{
      this.builder = this.builder.systemName(conf.getString("system.name"))
      setTransactionSigner
      setDIDContractCID
      setDIDContract
      setCustomContracts
      this.builder.exportGenesisBlock(fileName)
      r = true
    }catch {
      case e:Exception=>e.printStackTrace()
    }
    r
  }

  private def setDIDContract:Unit={
    setContractDeploy(this.conf,"system.did-contract.deploy")
    setAccountRegistration(this.conf,"system.did-contract.account-registration")
    setOperateRegistration(this.conf,"system.did-contract.operate-registration")
    setAuthorizesRegistration(this.conf,"system.did-contract.authorizes-registration")
    setCustomContractInvokes(this.conf,"system.did-contract.custom-contract-invokes")
  }

  private def setCustomContracts:Unit={
    val path = "system.custom-contracts"
    if(isExistConfigNode(this.conf,path)){
      val list = this.conf.getConfigList(path)
      if (list != null) {
        list.forEach(c=>{
          setCustomContract(c)
        })
      }
    }
  }

  private def setCustomContract(config:Config):Unit={
    if(config != null){
      setContractDeploy(config,"deploy")
      setAccountRegistration(config,"account-registration")
      setOperateRegistration(config,"operate-registration")
      setAuthorizesRegistration(config,"authorizes-registration")
      setCustomContractInvokes(config,"custom-contract-invokes")
    }
  }

  private def setCustomContractInvokes(config:Config,path:String):Unit={
    if(isExistConfigNode(config,path)) {
      val list = config.getConfigList(path)
      if (list != null) {
        list.forEach(t=>{
          val contract_name= t.getString("contract-name")
          val contract_version=t.getInt("contract-version")  //版本号需要配置为整数
          val method_name=t.getString("method-name")
          val method_parameter= this.getFileContent(t.getString("method-parameter")) //调用合约的参数从json文件中读取
          val signer = t.getString("transaction-signer")
          this.builder = this.builder.buildInvokeTransaction(ChaincodeId(contract_name,contract_version),method_name,method_parameter,signer)
        })
      }
    }
  }

  private def setAuthorizesRegistration(config:Config,path:String):Unit={
    if(isExistConfigNode(config,path)) {
      val list = config.getConfigList(path)
      if (list != null) {
        list.forEach(auth=>{
          val ops = getArrayOfString(auth,"actions-Granteds")
          val grantees = getArrayOfString(auth,"grantees")
          val isTransfer = auth.getBoolean("is-transfer")
          val transferType = if(isTransfer) TransferType.TRANSFER_REPEATEDLY else TransferType.TRANSFER_DISABLE
          val signer = auth.getString("transaction-signer")
          this.builder = this.builder.buildAuthorizes(ops,grantees,transferType,signer)
        })
      }
    }
  }

  private def setOperateRegistration(config:Config,path:String):Unit={
    if(isExistConfigNode(config,path)) {
      val list = config.getConfigList(path)
      if(list != null){
        val ops : ArrayBuffer[(String,String,Boolean,OperateType,String)] = new ArrayBuffer[(String,String,Boolean,OperateType,String)]()
        list.forEach(op=>{
          val opType = if(op.getBoolean("is-contract-operate"))  OperateType.OPERATE_CONTRACT else OperateType.OPERATE_SERVICE
          val signer = op.getString("transaction-signer")
          ops += Tuple5(op.getString("operate-name"),op.getString("operate-desc"),op.getBoolean("is-publish"),opType,signer)
          //this.builder = this.builder.buildOp(operate,signer)
        })
        if(ops.length > 0){
          this.builder = this.builder.buildOps(ops.toArray)
        }
      }
    }
  }

  private def setAccountRegistration(config:Config,path : String):Unit={
    if(isExistConfigNode(config,path)){
      val list = config.getConfigList(path)
      if(list != null){
        val signers : ArrayBuffer[(String,String,String,String)] = new ArrayBuffer[(String,String,String,String)]()
        list.forEach(account=>{
          val certName = account.getString("account-cert-name")
          val accountName = account.getString("account-name")
          val phoneCode = account.getString("phone-code")
          val signer = account.getString("transaction-signer")
          signers += Tuple4(certName,accountName,phoneCode,signer)
          //this.builder = this.builder.buildSigner((certName,accountName,phoneCode),signer)
        })
        if(signers.length > 0){
          this.builder = this.builder.buildSigners(signers.toArray)
        }
      }
    }
  }

  private def setContractDeploy(config:Config,path:String):Unit={
    if(isExistConfigNode(config,path)){
      val c = config.getConfig(path)
      if(c != null){
        val name = c.getString("contract-name")
        val version = c.getInt("contract-version")
        val code = c.getString("contract-code-path")
        val signer = c.getString("transaction-signer")
        var isCalledByOtherContracts : Boolean = false
        try{
          isCalledByOtherContracts = c.getBoolean("is-called-by-other-contracts")
        }catch {
          case e:Exception => isCalledByOtherContracts = false
        }
        this.builder = this.builder.buildDeploy(code,name,version,signer,isCalledByOtherContracts)
      }
    }

  }

  private def isExistConfigNode(config:Config,path:String):Boolean={
    var r : Boolean = false
    try{
      val c = config.getAnyRef(path)
      if(c != null){
        r = true
      }
    }catch {
      case e:Exception =>
        r = false
    }
    r
  }

  private def getArrayOfString(config:Config,path:String):Array[String]={
    var rl : Array[String] = Array()

    val tmpList = config.getStringList(path)
    if(tmpList != null){
      rl = new Array[String](tmpList.size())
      tmpList.toArray(rl)
    }
    rl
  }

  private def setTransactionSigner:Unit={
    val cSigners = this.conf.getConfigList("system.transaction-signer")
    val signers : ArrayBuffer[(String, String)] = new ArrayBuffer[(String, String)]()

    if(cSigners != null){
      cSigners.forEach(cs=>{
        val signer = (cs.getString("name"),cs.getString("pwd"))
        signers += signer
      })
    }

    if(signers.length <= 0){
      throw new Exception("No signer set")
    }else{
      this.builder = this.builder.buildTransactionSigners(signers.toArray)
    }
  }

  private def setDIDContractCID:Unit={
    this.builder = this.builder.chaincodeId4DID(this.conf.getString("system.did-contract.contract-name"),
                         this.conf.getInt("system.did-contract.contract-version"))
  }

  private def getFileContent(file:String):String={
    val s = scala.io.Source.fromFile(file, "UTF-8")
    val c = try s.mkString finally s.close()
    c
  }
}
