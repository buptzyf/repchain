package rep.utils.genesis

import rep.app.system.RepChainSystemContext
import rep.proto.rc2.Transaction
import rep.utils.IdTool

import scala.collection.mutable.ArrayBuffer

class GenesisBuilder {
  private var systemName : String = ""
  private var systemFullName : String = ""
  private var systemCredit : String = ""
  private var superName : String = ""
  private var superFullName : String = ""
  private var superCredit : String = ""
  private var superPwdOfPKey : String = ""


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

  def superNameAndPwd(name:String,pwd:String):GenesisBuilder={
    this.superName = name
    this.superPwdOfPKey = pwd
    loadSuperPrivateKey
    this
  }



  private def loadContext:Unit={
    this.ctx = new RepChainSystemContext(this.systemName)
    this.dir4key = ctx.getCryptoMgr.getKeyFileSuffix.substring(1)
    this.keySuffix = ctx.getCryptoMgr.getKeyFileSuffix
    this.NetworkId = ctx.getConfig.getChainNetworkId
    this.systemFullName = s"${ctx.getConfig.getIdentityNetName}${IdTool.DIDPrefixSeparator}${this.systemName}"
    this.superFullName = s"${ctx.getConfig.getIdentityNetName}${IdTool.DIDPrefixSeparator}${this.superName}"
    this.systemCredit = s"${ctx.getConfig.getIdentityNetName}${IdTool.DIDPrefixSeparator}${this.systemName.substring(0,this.systemName.lastIndexOf("."))}"
    this.superCredit = s"${ctx.getConfig.getIdentityNetName}${IdTool.DIDPrefixSeparator}${this.superName.substring(0,this.superName.lastIndexOf("."))}"

    ctx.getSignTool.loadPrivateKey(this.systemName, this.ctx.getConfig.getKeyStorePassword,
      s"${this.dir4key}" +
        s"/${this.NetworkId}/${this.systemName}${this.keySuffix}")
  }

  private def loadSuperPrivateKey:Unit={
    ctx.getSignTool.loadPrivateKey(this.superName, this.superPwdOfPKey,
      s"${this.dir4key}" +
        s"/${this.NetworkId}/${this.superName}${this.keySuffix}")
  }



}
