package rep.utils.genesis

import rep.app.system.RepChainSystemContext
import rep.proto.rc2.Transaction
import rep.utils.IdTool

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object GenesisBuilder{
  case class signerOfTransaction(name:String,fullName:String,credit:String,pwd:String)
}

class GenesisBuilder {
  import GenesisBuilder._
  private val hmOfTransactionSigner = new mutable.HashMap[String,signerOfTransaction]()
  private var systemName : String = ""



  private var ctx : RepChainSystemContext = null
  private val translist: ArrayBuffer[Transaction] = new ArrayBuffer[Transaction]

  private var dir4key : String = ""
  private var keySuffix : String = ""
  private var NetworkId : String = ""

  def systemName(name:String):GenesisBuilder={
    this.systemName = name
    loadContext
    this
  }

  def buildSigner(name:String,pwd:String):GenesisBuilder={
    val signer = signerOfTransaction(name,
      s"${ctx.getConfig.getIdentityNetName}${IdTool.DIDPrefixSeparator}${name}",
      s"${ctx.getConfig.getIdentityNetName}${IdTool.DIDPrefixSeparator}${name.substring(0,name.lastIndexOf("."))}",
      pwd
    )
    this.hmOfTransactionSigner += name -> signer
    loadKey(name,pwd)
    this
  }

  def buildSigners(signers:Array[(String,String)]):GenesisBuilder={
    if(signers != null){
      signers.foreach(s=>{
        val signer = signerOfTransaction(s._1,
          s"${ctx.getConfig.getIdentityNetName}${IdTool.DIDPrefixSeparator}${s._1}",
          s"${ctx.getConfig.getIdentityNetName}${IdTool.DIDPrefixSeparator}${s._1.substring(0,s._1.lastIndexOf("."))}",
          s._2
        )
        this.hmOfTransactionSigner += s._1 -> signer
        loadKey(s._1,s._2)
      })
    }
    this
  }

  private def loadContext:Unit={
    this.ctx = new RepChainSystemContext(this.systemName)
    this.dir4key = ctx.getCryptoMgr.getKeyFileSuffix.substring(1)
    this.keySuffix = ctx.getCryptoMgr.getKeyFileSuffix
    this.NetworkId = ctx.getConfig.getChainNetworkId
  }

  private def loadKey(name:String,pwd:String):Unit={
    ctx.getSignTool.loadPrivateKey(name, pwd,
      s"${this.dir4key}" +
        s"/${this.NetworkId}/${name}${this.keySuffix}")
  }



}
