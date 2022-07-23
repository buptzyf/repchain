package rep.utils.genesis

import java.io.PrintWriter
import com.google.protobuf.timestamp.Timestamp
import org.json4s.jackson.JsonMethods.{pretty, render}
import rep.app.system.RepChainSystemContext
import rep.network.consensus.util.BlockHelp
import rep.proto.rc2.Authorize.TransferType
import rep.proto.rc2.Certificate.CertType
import rep.proto.rc2.ChaincodeDeploy.{CodeType, RunType, StateType}
import rep.proto.rc2.Operate.OperateType
import rep.proto.rc2.{Authorize, Certificate, ChaincodeDeploy, ChaincodeId, Operate, Signer, Transaction}
import rep.storage.util.pathUtil
import rep.utils.{IdTool, SerializeUtils}
import scalapb.json4s.JsonFormat
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object GenesisBuilder {

  case class signerOfTransaction(name: String, fullName: String, credit: String, pwd: String)

}

/**
 * @author jiangbuyun
 * @version 2.0
 * @since 2022-07-22
 * @category 创世块生成的工具类
 **/
class GenesisBuilder {

  import GenesisBuilder._

  private val hmOfTransactionSigner = new mutable.HashMap[String, signerOfTransaction]()
  private var systemName: String = ""

  private var ctx: RepChainSystemContext = null
  private val translist: ArrayBuffer[Transaction] = new ArrayBuffer[Transaction]

  private var dir4key: String = ""
  private var keySuffix: String = ""
  private var NetworkId: String = ""

  private var cidOfDID = new ChaincodeId("RdidOperateAuthorizeTPL", 1)

  ////////////////////初始化部分 开始/////////////////////////////////////////////////////////
  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-07-22
   * @category 设置系统名称，装载系统的上下文
   * @param 	name :String 系统名称，样例：121000005l35120456.node1，系统装载指定系统名称的配置文件
   * @return 返回自身实例
   **/
  def systemName(name: String): GenesisBuilder = {
    this.systemName = name
    loadContext
    this
  }

  def chaincodeId4DID(name:String,version:Int):GenesisBuilder={
    this.cidOfDID = ChaincodeId(name,version)
    this
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-07-22
   * @category 建立创世块交易的签名者
   * @param 	name :String 交易签名者的名称，样例：121000005l35120456.node1
   * @param 	pwd  :String  交易签名者私钥的密码，交易签名者的私钥需要存放在指定的位置。
   * @return 返回自身实例
   **/
  def buildTransactionSigner(name: String, pwd: String): GenesisBuilder = {
    val signer = signerOfTransaction(name,
      s"${ctx.getConfig.getIdentityNetName}${IdTool.DIDPrefixSeparator}${name}",
      s"${ctx.getConfig.getIdentityNetName}${IdTool.DIDPrefixSeparator}${name.substring(0, name.lastIndexOf("."))}",
      pwd
    )
    this.hmOfTransactionSigner += name -> signer
    loadKey(name, pwd)
    this
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-07-22
   * @category 建立多个创世块交易的签名者
   * @param 	signers :Array[(String,String)] 交易签名者数组，数组的内容为二元组，二元组的内容如下：
   *                  name:String 交易签名者的名称，样例：121000005l35120456.node1
   *                  pwd:String  交易签名者私钥的密码，交易签名者的私钥需要存放在指定的位置。
   * @return 返回自身实例
   **/
  def buildTransactionSigners(signers: Array[(String, String)]): GenesisBuilder = {
    if (signers != null) {
      signers.foreach(s => {
        this.buildTransactionSigner(s._1, s._2)
      })
    }
    this
  }


  private def loadContext: Unit = {
    this.ctx = new RepChainSystemContext(this.systemName)
    this.dir4key = ctx.getCryptoMgr.getKeyFileSuffix.substring(1)
    this.keySuffix = ctx.getCryptoMgr.getKeyFileSuffix
    this.NetworkId = ctx.getConfig.getChainNetworkId
  }

  private def loadKey(name: String, pwd: String): Unit = {
    ctx.getSignTool.loadPrivateKey(name, pwd,
      s"${this.dir4key}" +
        s"/${this.NetworkId}/${name}${this.keySuffix}")
  }

  ////////////////////初始化部分 完成/////////////////////////////////////////////////////////

  ////////////////////DID相关部分 开始/////////////////////////////////////////////////////////
  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-07-22
   * @category 建立DID账户
   * @param 	signer   :(String,String,String) 账户信息，包括：账户证书名，账户ID，账户手机号，交易签名者 样例：("node1", "121000005l35120456", "18512345678"，121000005l35120456.node1)
   *                   账户的证书需要存放在指定的位置
   * @return 返回自身实例
   **/
  def buildSigner(signer: (String, String, String, String)): GenesisBuilder = {
    try {
      val id = s"${ctx.getConfig.getIdentityNetName}${IdTool.DIDPrefixSeparator}${signer._2}"

      val certFile = scala.io.Source.fromFile(s"${this.dir4key}/${this.NetworkId}/" +
        signer._2 + "." + signer._1 + ".cer", "UTF-8")
      val certStr = try certFile.mkString finally certFile.close()
      val certStrHash = ctx.getHashTool.hashstr(IdTool.deleteLine(certStr))
      val certId = IdTool.getCertIdFromCreditAndName(id, signer._1)
      val millis = System.currentTimeMillis()
      //生成Did的身份证书
      val authCert = Certificate(certStr, "SHA256withECDSA", true, Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)),
        _root_.scala.None, CertType.CERT_AUTHENTICATION, Option(certId), certStrHash, "1.0")
      //生成Did账户
      val signer_tmp = Signer(signer._1, id, signer._3, _root_.scala.Seq.empty,
        _root_.scala.Seq.empty, _root_.scala.Seq.empty, _root_.scala.Seq.empty, List(authCert), "",
        Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)), _root_.scala.None, true, "1.0")

      this.buildInvokeTransaction(cidOfDID, "signUpSigner", JsonFormat.toJsonString(signer_tmp), signer._4)
    } catch {
      case e: Exception =>
        e.printStackTrace()
    }
    this
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-07-22
   * @category 建立多个DID账户
   * @param 	signers  :Array[(String,String,String)] 账户数据数组，账户信息是一个三元组，包括：账户证书名，账户ID，账户手机号，合约部署交易的签名者的名称 样例：("node1", "121000005l35120456", "18512345678",121000005l35120456.node1)
   *                   账户的证书需要存放在指定的位置
   * @return 返回自身实例
   **/
  def buildSigners(signers: Array[(String, String, String,String)]): GenesisBuilder = {
    try {
      if (signers != null) {
        signers.foreach(s => {
          this.buildSigner(s)
        })
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
    }
    this
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-07-22
   * @category 建立DID操作
   * @param 	op       :(String,String,Boolean,OperateType) 操作信息，五元组包括：操作名，操作描述，是否公开,操作类型合约部署交易的签名者的名称，样例：("RdidOperateAuthorizeTPL.signUpSigner", "注册RDID",false,OperateType，121000005l35120456.node1)
   * @return 返回自身实例
   **/
  def buildOp(op: (String, String, Boolean,OperateType,  String)): GenesisBuilder = {
    try {
      val millis = System.currentTimeMillis()
      val snLs = List("transaction.stream", "transaction.postTranByString", "transaction.postTranStream", "transaction.postTran")
      val opName = s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}${op._1}"
      val opNameHash = ctx.getHashTool.hashstr(opName)
      val ownerCredit = this.hmOfTransactionSigner(op._5).credit
      // 公开操作，无需授权，普通用户可以绑定给自己的证书
      val tmpOperate = Operate(opNameHash, op._2, ownerCredit, op._3, op._4,
        snLs, "*", opName, Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)),
        _root_.scala.None, true, "1.0")
      this.buildInvokeTransaction(cidOfDID, "signUpOperate", JsonFormat.toJsonString(tmpOperate), op._5)
    } catch {
      case e: Exception =>
        e.printStackTrace()
    }
    this
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-07-22
   * @category 建立多个DID操作
   * @param 	ops      :Array[(String,String,Boolean,OperateType，String)] 操作信息数组，数组每个原始为五元组，包括：操作名，操作描述，是否公开，操作类型，合约部署交易的签名者的名称 样例：("RdidOperateAuthorizeTPL.signUpSigner", "注册RDID",false，OperateType，121000005l35120456.node1)
   * @return 返回自身实例
   **/
  def buildOps(ops: Array[(String, String, Boolean,OperateType,String)]): GenesisBuilder = {
    try {
      if (ops != null) {
        ops.foreach(op => {
          this.buildOp(op)
        })
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
    }
    this
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-07-22
   * @category 建立DID授权
   * @param 	actionsGranteds :Array[String] 被授权的操作 样例：Array("RdidOperateAuthorizeTPL.signUpSigner")
   * @param grantees          :Array[String] 被授权的人，样例：Array("951002007l78123233")
   * @param 	transfer        : TransferType 该授权是否传递，样例：TransferType.TRANSFER_REPEATEDLY
   * @param SignerName        :String 合约部署交易的签名者的名称，样例：121000005l35120456.node1
   * @return 返回自身实例
   **/
  def buildAuthorizes(actionsGranteds: Array[String], grantees: Array[String], transfer: TransferType, SignerName: String): GenesisBuilder = {
    try {
      if (actionsGranteds != null && grantees != null) {
        val als = new ArrayBuffer[String]
        val ownerCredit = this.hmOfTransactionSigner(SignerName).credit
        grantees.foreach(granted => {
          actionsGranteds.foreach(op => {
            val opHash = ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}${op}")
            val grantee = s"${ctx.getConfig.getIdentityNetName}${IdTool.DIDPrefixSeparator}${granted}"
            val gs = Array {
              grantee
            }
            val os = Array {
              opHash
            }

            val tmpmillis = System.currentTimeMillis()
            val at = Authorize(IdTool.getRandomUUID, ownerCredit, gs, os,
              transfer, Option(Timestamp(tmpmillis / 1000, ((tmpmillis % 1000) * 1000000).toInt)),
              _root_.scala.None, true, "1.0")
            als += JsonFormat.toJsonString(at)
          })
        })
        if (als.size > 0) {
          this.buildInvokeTransaction(cidOfDID, "grantOperate", SerializeUtils.compactJson(als), SignerName)
        }
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
    }
    this
  }

  ////////////////////DID相关部分 完成/////////////////////////////////////////////////////////


  ////////////////////通用部分 开始/////////////////////////////////////////////////////////
  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-07-22
   * @category 建立合约部署交易
   * @param 	ContractFileName :String 合约代码的文件路径，包含文件名
   * @param ContractName       :String 合约的名称
   * @param Version            :String 合约的版本号
   * @param SignerName         :String 合约部署交易的签名者的名称，样例：121000005l35120456.node1
   * @return 返回自身实例
   **/
  def buildDeploy(ContractFileName: String, ContractName: String, Version: Int, SignerName: String): GenesisBuilder = {
    try {
      val s1 = scala.io.Source.fromFile(ContractFileName, "UTF-8")
      val l1 = try s1.mkString finally s1.close()
      val cid = new ChaincodeId(ContractName, Version)
      val tran = ctx.getTransactionBuilder.createTransaction4Deploy(this.hmOfTransactionSigner(SignerName).fullName, cid, l1,
        "", 5000,
        CodeType.CODE_SCALA, RunType.RUN_SERIAL, StateType.STATE_BLOCK,
        ChaincodeDeploy.ContractClassification.CONTRACT_SYSTEM, 0)
      this.translist += tran
    } catch {
      case e: Exception => e.printStackTrace()
    }
    this
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-07-22
   * @category 通用建立合约调用交易
   * @param 	cid            :ChaincodeId 合约Id
   * @param 	methodName     :String  调用合约的方法名
   * @param 	inputParameter :String 调用合约方法的输入参数，参数是JSON字符串
   * @param SignerName       :String 合约部署交易的签名者的名称，样例：121000005l35120456.node1
   * @return 返回自身实例
   **/
  def buildInvokeTransaction(cid: ChaincodeId, methodName: String, inputParameter: String, SignerName: String): GenesisBuilder = {
    try {
      translist += ctx.getTransactionBuilder.createTransaction4Invoke(
        this.hmOfTransactionSigner(SignerName).fullName, cid,
        methodName, Seq(inputParameter))
    } catch {
      case e: Exception =>
        e.printStackTrace()
    }
    this
  }

  ////////////////////通用部分 完成/////////////////////////////////////////////////////////

  ////////////////////导出创世块到文件 开始/////////////////////////////////////////////////////////
  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-07-22
   * @category 通用建立合约调用交易
   * @param 	fileName :String 导出的文件名，不包含文件扩展名
   * @return
   **/
  def exportGenesisBlock(fileName: String): Unit = {
    var pw: PrintWriter = null
    try {
      var blk = BlockHelp.buildBlock("", 1, translist)
      blk = blk.withHeader(blk.getHeader.clearEndorsements)
      blk = blk.clearTransactionResults
      val r = JsonFormat.toJson(blk)
      val jsonStr = pretty(render(r))
      println(jsonStr)
      val path = s"json/${ctx.getConfig.getChainNetworkId}"
      pathUtil.MkdirAll(path)
      val fullName = if (ctx.getConfig.isUseGM) fileName + "_gm.json" else fileName + ".json"
      pw = new PrintWriter(s"${path}/${fullName}", "UTF-8")
      pw.write(jsonStr)
      pw.flush()
    } catch {
      case e: Exception =>
        e.printStackTrace()
    } finally {
      if (pw != null) pw.close()
    }

  }

  ////////////////////导出创世块到文件 开始/////////////////////////////////////////////////////////
}
