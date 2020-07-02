package rep.sc.tpl

import com.google.protobuf.timestamp.Timestamp
import org.bouncycastle.util.encoders.Base64
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse
import rep.app.conf.SystemProfile
import rep.authority.cache.opcache.IOperateCache
import rep.crypto.cert.SignTool
import rep.protos.peer.{ActionResult, Signature}
import rep.sc.scalax.{ContractContext, ContractException, IContract}
import rep.utils.SerializeUtils
import scala.collection.mutable.ArrayBuffer

/**
 * @ Description: 多方签名
 * @ Author: daiyongbing
 * @ CreateDate: 2020/06/29
 * @ Version: 1.0
 */
final case class Prove(unsignedText: String, sign: String)
final case class Myabc(tmp: ArrayBuffer[String])
final case class Mydef(tmp: ArrayBuffer[String])
final case class sig(name:String, sign:String)

class MultiSignErr extends IContract {

  override def init(ctx: ContractContext) {
    println(s"tid: $ctx.t.id")
  }

  implicit val formats: DefaultFormats.type = DefaultFormats
  val chaincodeName: String = SystemProfile.getAccountChaincodeName
  val chaincodeVersion: Int = SystemProfile.getAccountChaincodeVersion

  object ACTION {
    val MULTI_SIGN = "multiSign"
  }

  def multiSign(ctx: ContractContext, data: Prove): ActionResult = {
    // 待签名内容
    val unsignedText: String = data.unsignedText
    //当前签名
    val currentSignature: String = data.sign
    //判空
    if (unsignedText == null || "".equals(unsignedText.trim) || currentSignature == null || "".equals(currentSignature.trim)) {
      throw ContractException("unsignedText和signature不能为空")
    }
    // 获取当前交易的CertId和creditCode
    val tCertId = ctx.t.getSignature.getCertId

    //解码当前签名
    val bytes = Base64.decode(currentSignature)
    //使用公钥验证签名
    val flag = SignTool.verify(bytes, unsignedText.getBytes(), tCertId, ctx.api.pe.getSysTag)
    // 如果验证失败，则抛出异常
    if (!flag) {
      throw ContractException("签名验证失败")
    }
    //如果验证成功，构建新的Signature
    val signMillis: Long = System.currentTimeMillis + 8 * 3600 * 1000
    val signTime: Timestamp = Timestamp.defaultInstance.withSeconds(signMillis / 1000).withNanos((signMillis % 1000).toInt * 1000000)
    //    val newsignature: Signature = Signature(Some(tCertId), Some(signTime), ByteString.copyFrom(bytes))
    //    println(s"newsignature: $newsignature")

    //取出待签名unsignedText的签名集合
    val oldSignatures = ctx.api.getState(unsignedText)

    var tmp1: Myabc = null
    if (null == oldSignatures) { //如果oldSignatures为空，说明是首次签名，新建Signatures
      //signatures = Signatures(Seq.newBuilder.+=(newsignature).result())
      //signatures = Signatures(Set(newsignature))
      /*tmp1 = new Myabc(new ArrayBuffer[String](10))
      tmp1.tmp += "String"
      println(s"first signatures: $tmp1")
      var ll = new Mydef(new ArrayBuffer[String]())
      ll.tmp += "sss"*/
       val tmpsig = sig("sss","")
    } else { //如果不为空，反序列化为Signatures对象，并将新的签名添加到Signatures中，成为新的签名集合
      tmp1 = SerializeUtils.deserialise(oldSignatures).asInstanceOf[Myabc]
      tmp1.->("newsignature")
      println(s"new signatures: $tmp1")
    }

    // 将新的signatures写回区块链
    ctx.api.setVal(unsignedText, tmp1)

    //调用响应

    //ActionResult(200, "认证通过，签名：" + tmp1)
    ActionResult(200, "认证通过，签名：" + "")
  }

  override def onAction(ctx: ContractContext, action: String, sdata: String): ActionResult = {
    val json = parse(sdata)
    action match {
      case ACTION.MULTI_SIGN =>
        println("multiSign")
        multiSign(ctx, json.extract[Prove])
    }
  }
}
