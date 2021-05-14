package rep.sc.tpl

import java.io.StringReader
import java.security.Security
import java.security.cert.X509CRL

import org.bouncycastle.cert.X509CRLHolder
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter
import org.bouncycastle.cert.ocsp.OCSPResp
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.util.io.pem.PemReader
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse
import rep.protos.peer.ActionResult
import rep.sc.scalax.{ContractContext, ContractException, IContract}
import rep.ssl.CustomSSLEngine

case class NodeOcspResp(serialNumber: String, ocspRespPem: String)

/**
  * @author zyf
  */
class ManageNodeCert extends IContract {

  override def init(ctx: ContractContext): Unit = {
    Security.addProvider(new BouncyCastleProvider)
    println(s"tid: ${ctx.t.id}, execute the contract which name is ${ctx.t.getCid.chaincodeName} and version is ${ctx.t.getCid.version}")
  }

  /**
    * 清除初始化crl列表，这样后面从levelDB检索的时候才能生效，不然会优先从certStore中读取，这个可以放在接口处即可，不用广播入块
    *
    * @param ctx
    * @return
    */
  def clearInitializedCrlList(ctx: ContractContext, flag: Boolean): ActionResult = {
    if (flag && ctx.api.getVal("crl" + "_" + ctx.t.getSignature.getCertId.creditCode) != null) {
      CustomSSLEngine.clearCrlList(ctx)
    } else {
      throw ContractException("未持久化CRL列表到levelDB中，暂时不能清除初始化到CertStore中的CRL")
    }
    null
  }

  /**
    * 吊销节点证书，将crl列表写到CertStore中，其实也可对外提供查询，但是不能保证入块，在缓存中，没有持久化到levelDB
    *
    * @param ctx
    * @param crlPem
    * @return
    */
  def updateCrlToCertStore(ctx: ContractContext, crlPem: String): ActionResult = {
    val pemParser = new PEMParser(new StringReader(crlPem))
    val crlHolder = pemParser.readObject().asInstanceOf[X509CRLHolder]
    val crl = new JcaX509CRLConverter().setProvider("BC").getCRL(crlHolder)
    CustomSSLEngine.updateCrlList(ctx, crl)
    null
  }

  /**
    * 吊销节点证书，将crl列表写到levelDB中，然后可通过接口读取
    *
    * @param ctx
    * @param crlPem
    * @return
    */
  def updateCrlToDb(ctx: ContractContext, crlPem: String): ActionResult = {
    if (ctx.api.bSuperAdmin(ctx)) {
      val pemParser = new PEMParser(new StringReader(crlPem))
      val crlHolder = pemParser.readObject().asInstanceOf[X509CRLHolder]
      val crl = new JcaX509CRLConverter().setProvider("BC").getCRL(crlHolder)
      // 放在super_admin下
      ctx.api.setVal("crl" + "_" + ctx.t.getSignature.getCertId.creditCode, crl)
      null
    } else {
      throw ContractException("非super_admin")
    }
  }

  /**
    *
    * @param ctx
    * @param nodeOcspResp
    * @return
    */
  def updateOcspRespToDB(ctx: ContractContext, nodeOcspResp: NodeOcspResp): ActionResult = {
    if (ctx.api.bSuperAdmin(ctx)) {
      val pemReader = new PemReader(new StringReader(nodeOcspResp.ocspRespPem))
      val ocspRespBytes = pemReader.readPemObject().getContent
      val ocspResp = new OCSPResp(ocspRespBytes)
      // 放在serailNumber下
      ctx.api.setVal("ocsp" + "_" + nodeOcspResp.serialNumber, ocspResp)
      null
    } else {
      throw ContractException("非super_admin")
    }
  }

  override def onAction(ctx: ContractContext, action: String, sdata: String): ActionResult = {

    implicit val formats: DefaultFormats.type = DefaultFormats
    val json = parse(sdata)

    action match {
      case "clearInitializedCrlList" => clearInitializedCrlList(ctx, json.extract[Boolean])
      case "updateCrlToCertStore" => updateCrlToCertStore(ctx, json.extract[String])
      case "updateCrlToDb" => updateCrlToDb(ctx, json.extract[String])
      case "updateOcspRespToDB" => updateOcspRespToDB(ctx, json.extract[NodeOcspResp])
      case _ => throw ContractException("no method matched")
    }
  }
}
