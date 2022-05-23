package rep.sc.tpl.cooper

import org.bouncycastle.util.io.pem.PemReader
import org.json4s.jackson.JsonMethods.parse
import org.json4s.jackson.Serialization.{read, writePretty}
import org.json4s.{DefaultFormats, MappingException}
import rep.app.conf.SystemProfile
import rep.crypto.BytesHex
import rep.protos.peer.{ActionResult, Certificate}
import rep.sc.scalax.{ContractContext, ContractException, IContract}
import rep.sc.tpl.did.DidTplPrefix.certPrefix
import rep.utils.SerializeUtils

import java.io.{ByteArrayInputStream, StringReader}
import java.nio.charset.StandardCharsets
import java.security.PublicKey
import java.security.cert.{CertificateFactory, X509Certificate}


/**
 * 接口定义
 *
 * @param id           接口定义ID
 * @param `type`       接口定义的类型，1：grpc，2：wsdl
 * @param version      接口定义版本号
 * @param name         接口名
 * @param algo_hash    生成内容 Hash 采用的算法
 * @param algo_sign    对内容 Hash 的生成签名的算法
 * @param para         参数数据结构定义
 * @param serv         接口服务定义，包括初始接口请求、中间接口请求、结束接口请求三者的方法及参数定义
 * @param serv_doc     接口服务说明
 * @param callBack     应答接收定义，包括初始接口应答、中间接口应答、结束接口应答三者的方法及参数定义
 * @param callback_doc 应答接收说明
 */
final case class ApiDefinition(id: String, `type`: String, version: String, name: String, algo_hash: String, algo_sign: String, para: Option[String],
                               serv: Option[String], serv_doc: Option[String], callBack: Option[String], callback_doc: Option[String])

/**
 * 接口服务和应答
 *
 * @param id      接口服务 Id 或 应答接收 Id
 * @param name    接口服务或者应答名字
 * @param d_id    接口服务所实现的接口定义 Id 或 应答接收所实现的接口定义 Id
 * @param version 接口服务所实现的接口定义版本 或 应答接收所实现的接口定义版本
 * @param e_id    提供接口服务的参与方 Id 或 提供应答接收的参与方 Id
 * @param addr    接口服务地址 或 应答接收地址
 * @param port    接口服务端口 或 应答接收端口
 */
final case class ApiServAndAck(id: String, name: String, d_id: String, version: String, e_id: String, addr: String, port: Int)

/**
 *
 * @param eid        身份ID
 * @param cert_name  证书别名
 * @param hash       内容Hash
 * @param timeCreate 签名时刻
 * @param sign       数字签名
 */
final case class Signature(eid: String, cert_name: String, hash: String, timeCreate: Long, sign: String)

/**
 *
 * @param cid        接口请求 Id
 * @param e_from     请求方的应答接收 Id
 * @param e_to       请求的接口服务 Id
 * @param method     调用的方法
 * @param b_req      请求 or 应答标志, True 代表请求; False 代表应答
 * @param b_end      结束标志, True 代表结束（即本次请求/应答为最后一个）,False代表未结束
 * @param seq        请求或应答的序号, 从1开始
 * @param hash       请求/应答内容 Hash依据 b_req 和 b_end 的值，分别对应 rb、ri、re 和 cb、ci、ce 方法的请求/应答内容的按照接口定义中指定的 Hash 算法生成的 Hash
 * @param hash_claim 选择性披露 Hash，最后一个应答后，由所有请求和应答的 Hash 按顺序拼接后取 Hash 生成
 * @param tm_create  请求/应答建立的时间
 * @param sign_r     接口请求方按照接口定义中指定的签名算法对内容 Hash 的签名
 * @param sign_c     接口服务方按照接口定义中指定的签名算法对内容 Hash 的签名
 */
final case class ReqAckProof(cid: String, e_from: String, e_to: String, method: String, b_req: Boolean, b_end: Boolean, seq: Int,
                             hash: String, hash_claim: Option[String], tm_create: Long, sign_r: Signature, sign_c: Signature)

/**
 * @author zyf
 */
class InterfaceCooperation extends IContract {

  // 从账户管理合约中读取账户 ID
  val contractCert = "RdidOperateAuthorizeTPL"
  // 分割下划线
  val underline = "_"

  val didTplName = SystemProfile.getAccountChaincodeName
  // Json序列化与反序列化时使用的格式
  implicit val formats = DefaultFormats

  object ACTION {

    object InterfaceRegister {
      val registerApiDefinition = "registerApiDefinition"
      val registerApiService = "registerApiService"
      val registerApiAckReceive = "registerApiAckReceive"
    }

    object InterfaceReqAckProof {
      val reqAckProof = "reqAckProof"
    }

  }

  override def init(ctx: ContractContext): Unit = {

    println(s"init contract, tx‘s id is ${ctx.t.id}, contract’s name is ${ctx.t.getCid.chaincodeName}, contract’s version is ${ctx.t.getCid.version}")
    ctx.api.getLogger.info(s"init contract, tx‘s id is ${ctx.t.id}, contract’s name is ${ctx.t.getCid.chaincodeName}, contract’s version is ${ctx.t.getCid.version}")

  }

  /**
   * 注册接口定义
   *
   * @param ctx
   * @param apiDefinition
   * @return
   */
  def registerApiDefinition(ctx: ContractContext, apiDefinition: ApiDefinition): ActionResult = {
    val txr_credit_code = ctx.t.getSignature.getCertId.creditCode
    val def_key = "api_def_main" + underline + txr_credit_code + underline + apiDefinition.id + underline + apiDefinition.version
    val def_version_key = "api_def_version" + underline + txr_credit_code + underline + apiDefinition.id + underline + apiDefinition.version
    val def_holder_key = "api_def_holder" + underline + apiDefinition.id
    // 判断是否是自己注册的
    if (ctx.api.getVal(def_holder_key) == null || ctx.api.getVal(def_holder_key) == txr_credit_code) {
      // 自己先前注册过，现在要注册的是新的版本
      if (ctx.api.getVal(def_key) != null) {
        ctx.api.getLogger.info(s"接口定义方 $txr_credit_code 更新定义API ${writePretty(apiDefinition)}")
      }
      // 保存apiDefinition，使用jsonString
      ctx.api.setVal(def_key, writePretty(apiDefinition))
      // 设置当前版本号
      ctx.api.setVal(def_version_key, apiDefinition.version)
      // 设置接口定义者，即该接口定义是谁来定义的，只能由此人进行更新
      ctx.api.setVal(def_holder_key, txr_credit_code)
      ctx.api.getLogger.info(s"接口定义方 $txr_credit_code 定义API ${writePretty(apiDefinition)}")
    } else {
      throw ContractException(s"用户 $txr_credit_code 非注册接口定义者,不能执行更新操作")
    }
    null
  }

  /**
   * 接口服务登记，接口服务方来调用该方法
   * 接口服务方或者代理方 发布指定接口定义的接口请求实现实例
   *
   * @param ctx
   * @param apiServAndAck
   * @return
   */
  def registerApiService(ctx: ContractContext, apiServAndAck: ApiServAndAck): ActionResult = {
    val txr_credit_code = ctx.t.getSignature.getCertId.creditCode
    val def_holder_key = "api_def_holder" + underline + apiServAndAck.d_id
    val service_holder_key = "api_service_holder" + underline + apiServAndAck.id
    val def_holder = ctx.api.getVal(def_holder_key)
    if (def_holder != null) {
      val def_key = "api_def_main" + underline + def_holder.asInstanceOf[String] + underline + apiServAndAck.d_id + underline + apiServAndAck.version
      // 判断接口定义(id+version)是否存在
      if (ctx.api.getVal(def_key) != null) {
        // 判断参与方是否被注册,且是e_id == credit_code
        if (txr_credit_code == apiServAndAck.e_id) {
          ctx.api.setVal("api_service_main" + underline + apiServAndAck.e_id + underline + apiServAndAck.id, writePretty(apiServAndAck))
          ctx.api.setVal(service_holder_key, apiServAndAck.e_id)
          ctx.api.getLogger.info(s"接口服务方/代理方 ${txr_credit_code} 发布接口服务登记 ${writePretty(apiServAndAck)}")
        } else {
          ctx.api.getLogger.error(s"e_id ${apiServAndAck.e_id} != credit_code $txr_credit_code")
          throw ContractException(s"e_id ${apiServAndAck.e_id} != credit_code $txr_credit_code")
        }
      } else {
        ctx.api.getLogger.error(s"id为${apiServAndAck.d_id}, 版本为${apiServAndAck.version} 的接口定义不存在")
        throw ContractException(s"id为${apiServAndAck.d_id}, 版本为${apiServAndAck.version} 的接口定义不存在")
      }
    } else {
      ctx.api.getLogger.error(s"id为${apiServAndAck.d_id} 的接口定义不存在")
      throw ContractException(s"id为${apiServAndAck.d_id} 的接口定义不存在")
    }
    null
  }

  /**
   * 应答接收登记，接口请求方来调用该方法
   * 接口请求方或者代理方发布指定接口定义的接口应答实现实例
   *
   * @param ctx
   * @param apiServAndAck
   * @return
   */
  def registerApiAckReceive(ctx: ContractContext, apiServAndAck: ApiServAndAck): ActionResult = {
    val txr_credit_code = ctx.t.getSignature.getCertId.creditCode
    val def_holder_key = "api_def_holder" + underline + apiServAndAck.d_id
    val ack_holder_key = "ack_receive_holder" + underline + apiServAndAck.id
    val def_holder = ctx.api.getVal(def_holder_key)
    if (def_holder != null) {
      val def_key = "api_def_main" + underline + def_holder.asInstanceOf[String] + underline + apiServAndAck.d_id + underline + apiServAndAck.version
      // 判断接口定义(id+version)是否存在
      if (ctx.api.getVal(def_key) != null) {
        // 判断参与方是否被注册,且是e_id == credit_code
        // TODO 判断服务是否已经被注册？
        if (txr_credit_code == apiServAndAck.e_id) {
          ctx.api.setVal("ack_receive_main" + underline + apiServAndAck.e_id + underline + apiServAndAck.id, writePretty(apiServAndAck))
          ctx.api.setVal(ack_holder_key, apiServAndAck.e_id)
          ctx.api.getLogger.info(s"接口服务方/代理方 ${txr_credit_code} 发布接口应答登记 ${writePretty(apiServAndAck)}")
        } else {
          ctx.api.getLogger.error(s"e_id ${apiServAndAck.e_id} != credit_code $txr_credit_code")
          throw ContractException(s"e_id ${apiServAndAck.e_id} != credit_code $txr_credit_code")
        }
      } else {
        ctx.api.getLogger.error(s"id为${apiServAndAck.d_id}, 版本为${apiServAndAck.version} 的接口定义不存在")
        throw ContractException(s"id为${apiServAndAck.d_id}, 版本为${apiServAndAck.version} 的接口定义不存在")
      }
    } else {
      ctx.api.getLogger.error(s"id为${apiServAndAck.d_id} 的接口定义不存在")
      throw ContractException(s"id为${apiServAndAck.d_id} 的接口定义不存在")
    }
    null
  }

  /**
   * 请求存证：请求方或者代理方提交交易提交签名交易
   * 应答存证：服务方或者代理方提交交易提交签名交易
   *
   * @param ctx
   * @param reqAckProof
   * @return
   */
  def reqAckProof(ctx: ContractContext, reqAckProof: ReqAckProof): ActionResult = {
    val txr_credit_code = ctx.t.getSignature.getCertId.creditCode
    // 应答注册者
    val ack_holder_key = "ack_receive_holder" + underline + reqAckProof.e_from
    // 服务注册者
    val service_holder_key = "api_service_holder" + underline + reqAckProof.e_to
    val ack_holder = ctx.api.getVal(ack_holder_key)
    val service_holder = ctx.api.getVal(service_holder_key)
    // 可根据是否有应答注册者或服务注册者来判断接口服务以及判断接口应答是否被登记
    if (service_holder != null && ack_holder != null) {
      // e_from与e_to的校验
      // 请求方注册的应答接收
      val ack_receive = ctx.api.getVal("ack_receive_main" + underline + ack_holder.asInstanceOf[String] + underline + reqAckProof.e_from)
      val servAndAck_receive = read[ApiServAndAck](ack_receive.asInstanceOf[String])
      // 服务方注册的接口服务
      val api_service = ctx.api.getVal("api_service_main" + underline + service_holder.asInstanceOf[String] + underline + reqAckProof.e_to)
      val servAndAck_service = read[ApiServAndAck](api_service.asInstanceOf[String])
      if (reqAckProof.b_req) {
        // 交易提交者需是接口请求方，由e_from 获得 ack_receive 来判断，判断应答接收注册者是否就是交易提交者
        if (servAndAck_receive.e_id == txr_credit_code) {
          // 对Signature中的 e_id 进行校验
          if (servAndAck_receive.e_id == reqAckProof.sign_r.eid && servAndAck_service.e_id == reqAckProof.sign_c.eid) {
            // 使用 e_from 或 e_to 可以关联到登记应答接收和接口服务的参与方，并获得其绑定的证书，关联到定义，拿到签名算法
            // 接口定义持有者
            val def_holder_key = "api_def_holder" + underline + servAndAck_receive.d_id
            val def_key = "api_def_main" + underline + ctx.api.getVal(def_holder_key) + underline + servAndAck_receive.d_id + underline + servAndAck_receive.version
            val api_def = read[ApiDefinition](ctx.api.getVal(def_key).asInstanceOf[String])
            val sig_alg = api_def.algo_sign
            ctx.api.getLogger.info(s"def_key 为 $def_key, api_def 为 $api_def, sig_alg为 $sig_alg")
            // 验证请求方
            val req_x509cert = getX509Cert(ctx, reqAckProof.sign_r.eid, reqAckProof.sign_r.cert_name)
            val req_verifyRes = verify(sig_alg, BytesHex.hex2bytes(reqAckProof.sign_r.sign), reqAckProof.sign_r.hash.getBytes(StandardCharsets.UTF_8), req_x509cert.getPublicKey)
            ctx.api.getLogger.info(s"对请求方签名数据进行验签, credit_code 为 ${reqAckProof.sign_r.eid}, cert_name ${reqAckProof.sign_r.cert_name}, cert $req_x509cert, verify_req_res $req_verifyRes")
            // 验证服务方
            val resp_x509cert = getX509Cert(ctx, reqAckProof.sign_c.eid, reqAckProof.sign_c.cert_name)
            val resp_verifyRes = verify(sig_alg, BytesHex.hex2bytes(reqAckProof.sign_c.sign), reqAckProof.sign_c.hash.getBytes(StandardCharsets.UTF_8), resp_x509cert.getPublicKey)
            ctx.api.getLogger.info(s"对服务方签名数据进行验签, credit_code 为 ${reqAckProof.sign_c.eid}, cert_name ${reqAckProof.sign_c.cert_name}, cert $resp_x509cert, verify_resp_res $resp_verifyRes")
            if (!req_verifyRes || !resp_verifyRes) {
              throw ContractException(s"请求方签名验签结果为$req_verifyRes, 服务方签名验签结果为$resp_verifyRes")
            }
          } else if (servAndAck_receive.e_id != reqAckProof.sign_r.eid && servAndAck_service.e_id == reqAckProof.sign_c.eid) {
            throw ContractException(s"sign_r e_id ${reqAckProof.sign_r.eid} 不等于接口请求方 credit_code ${servAndAck_receive.e_id}")
          } else if (servAndAck_receive.e_id == reqAckProof.sign_r.eid && servAndAck_service.e_id != reqAckProof.sign_c.eid) {
            throw ContractException(s"sign_c e_id ${reqAckProof.sign_c.eid} 不等于接口服务方 credit_code ${servAndAck_service.e_id}")
          } else {
            throw ContractException(s"sign_r e_id ${reqAckProof.sign_r.eid} 不等于接口请求方 credit_code ${servAndAck_service.e_id}, sign_c e_id ${reqAckProof.sign_c.eid} 不等于接口服务方 credit_code ${reqAckProof.sign_c.eid}")
          }
          ctx.api.getLogger.info(s"接口请求存证,请求id为${reqAckProof.cid},序号为${reqAckProof.seq},数据为${writePretty(reqAckProof)}")
          ctx.api.setVal("req_ack_proof_request" + underline + reqAckProof.cid + underline + reqAckProof.seq, writePretty(reqAckProof))
        } else {
          ctx.api.getLogger.error(s"接口请求方 e_id ${servAndAck_service.e_id} != credit_code $txr_credit_code")
          throw ContractException(s"接口请求方 ${servAndAck_service.e_id} != credit_code $txr_credit_code")
        }
      } else {
        // 交易提交者需是接口服务方，由e_to 获得 api_service 来判断，判断应答注册者是否就是交易提交者
        if (servAndAck_service.e_id == txr_credit_code) {
          // 对Signature中的 e_id 进行校验
          if (servAndAck_service.e_id == reqAckProof.sign_c.eid && servAndAck_receive.e_id == reqAckProof.sign_r.eid) {
            // 使用 e_from 或 e_to 可以关联到登记应答接收和接口服务的参与方，并获得其绑定的证书，关联到定义，拿到签名算法
            // 接口定义持有者
            val def_holder_key = "api_def_holder" + underline + servAndAck_service.d_id
            val def_key = "api_def_main" + underline + ctx.api.getVal(def_holder_key) + underline + servAndAck_service.d_id + underline + servAndAck_service.version
            val api_def = read[ApiDefinition](ctx.api.getVal(def_key).asInstanceOf[String])
            val sig_alg = api_def.algo_sign
            ctx.api.getLogger.info(s"def_key 为 $def_key, api_def 为 $api_def, sig_alg为 $sig_alg")
            // 验证服务方
            val resp_x509cert = getX509Cert(ctx, reqAckProof.sign_c.eid, reqAckProof.sign_c.cert_name)
            val resp_verifyRes = verify(sig_alg, BytesHex.hex2bytes(reqAckProof.sign_c.sign), reqAckProof.sign_c.hash.getBytes(StandardCharsets.UTF_8), resp_x509cert.getPublicKey)
            ctx.api.getLogger.info(s"对服务方签名数据进行验签, credit_code 为 ${reqAckProof.sign_c.eid}, cert_name ${reqAckProof.sign_c.cert_name}, cert $resp_x509cert, verify_resp_res $resp_verifyRes")
            // 验证请求方
            val req_x509cert = getX509Cert(ctx, reqAckProof.sign_r.eid, reqAckProof.sign_r.cert_name)
            val req_verifyRes = verify(sig_alg, BytesHex.hex2bytes(reqAckProof.sign_r.sign), reqAckProof.sign_r.hash.getBytes(StandardCharsets.UTF_8), req_x509cert.getPublicKey)
            ctx.api.getLogger.info(s"对请求方签名数据进行验签, credit_code 为 ${reqAckProof.sign_r.eid}, cert_name ${reqAckProof.sign_r.cert_name}, cert $req_x509cert, verify_req_res $req_verifyRes")
            if (!resp_verifyRes || !req_verifyRes) {
              throw ContractException(s"请求方签名验签结果为$req_verifyRes, 服务方签名验签结果为$resp_verifyRes")
            }
          } else if (servAndAck_service.e_id != reqAckProof.sign_c.eid && servAndAck_receive.e_id == reqAckProof.sign_r.eid) {
            throw ContractException(s"sign_c e_id ${reqAckProof.sign_c.eid} 不等于接口服务方 credit_code ${servAndAck_service.e_id}")
          } else if (servAndAck_service.e_id == reqAckProof.sign_c.eid && servAndAck_receive.e_id != reqAckProof.sign_r.eid) {
            throw ContractException(s"sign_r e_id ${reqAckProof.sign_r.eid} 不等于接口请求方 credit_code ${servAndAck_receive.e_id}")
          } else {
            throw ContractException(s"sign_r e_id ${reqAckProof.sign_r.eid} 不等于接口请求方 credit_code ${servAndAck_receive.e_id}, sign_c e_id ${reqAckProof.sign_c.eid} 不等于接口服务方 credit_code ${servAndAck_service.e_id}")
          }
          ctx.api.getLogger.info(s"接口应答存证,请求id为${reqAckProof.cid},序号为${reqAckProof.seq},数据为${writePretty(reqAckProof)}")
          ctx.api.setVal("req_ack_proof_response" + underline + reqAckProof.cid + underline + reqAckProof.seq, writePretty(reqAckProof))
        } else {
          ctx.api.getLogger.error(s"接口服务方 e_id ${servAndAck_receive.e_id} 不等于交易提交者 credit_code $txr_credit_code")
          throw ContractException(s"接口服务方 e_id ${servAndAck_receive.e_id} 不等于交易提交者 credit_code $txr_credit_code")
        }
      }
    } else if (service_holder == null && ack_holder != null) {
      ctx.api.getLogger.error(s"没有接口服务id为 ${reqAckProof.e_to} 的接口服务登记")
      throw ContractException(s"没有接口服务id为 ${reqAckProof.e_to} 的接口服务登记")
    } else if (service_holder != null && ack_holder == null) {
      ctx.api.getLogger.error(s"没有接口应答id为 ${reqAckProof.e_from} 的接口应答登记")
      throw ContractException(s"没有接口应答id为 ${reqAckProof.e_from} 的接口应答登记")
    } else {
      ctx.api.getLogger.error(s"没有接口服务id为 ${reqAckProof.e_to} 的接口服务登记, 且没有接口应答id为 ${reqAckProof.e_from} 的接口应答登记")
      throw ContractException(s"没有接口服务id为 ${reqAckProof.e_to} 的接口服务登记, 且没有接口应答id为 ${reqAckProof.e_from} 的接口应答登记")
    }
    null
  }

  /**
   * 用来验证内容签名
   *
   * @param alg       签名验签算法
   * @param signature 签名数据
   * @param message   被签名的数据
   * @param publicKey 公钥
   * @return
   */
  def verify(alg: String, signature: Array[Byte], message: Array[Byte], publicKey: PublicKey): Boolean = {
    if (publicKey == null) throw ContractException("验证签名时公钥为空！")
    if (signature == null || signature.length <= 0) throw ContractException("待验证的签名信息为空！")
    if (message == null || message.length <= 0) throw ContractException("待签名内容为空！")
    try {
      val s2 = java.security.Signature.getInstance(alg)
      s2.initVerify(publicKey)
      s2.update(message)
      s2.verify(signature)
    } catch {
      case ex: Exception => throw ContractException(ex.getMessage)
    }
  }

  /**
   * 根据证书pem字符串，构造证书，construct certificate by pemString
   *
   * @param ctx
   * @param creditCode
   * @param certName
   * @throws Exception
   * @return X509Certificate
   */
  @throws[Exception]
  def getX509Cert(ctx: ContractContext, creditCode: String, certName: String): X509Certificate = {
    val cert = SerializeUtils.deserialise(ctx.api.getStateEx(didTplName, certPrefix + creditCode + "." + certName)).asInstanceOf[Certificate]
    // cert.certificate 是读取pem证书文件得到的字符串
    val stringReader = new StringReader(cert.certificate)
    val pemReader = new PemReader(stringReader)
    val cf = CertificateFactory.getInstance("X.509")
    val certByte = pemReader.readPemObject.getContent
    val x509Cert = cf.generateCertificate(new ByteArrayInputStream(certByte)).asInstanceOf[X509Certificate]
    pemReader.close()
    stringReader.close
    x509Cert
  }


  override def onAction(ctx: ContractContext, action: String, sdata: String): ActionResult = {

    // Json序列化与反序列化时使用的格式
    implicit val formats = DefaultFormats

    val json = parse(sdata)

    try {
      action match {
        // 接口定义及相关登记
        case ACTION.InterfaceRegister.registerApiDefinition => registerApiDefinition(ctx, json.extract[ApiDefinition])
        // 接口服务登记
        case ACTION.InterfaceRegister.registerApiService => registerApiService(ctx, json.extract[ApiServAndAck])
        // 接口应答登记
        case ACTION.InterfaceRegister.registerApiAckReceive => registerApiAckReceive(ctx, json.extract[ApiServAndAck])
        // 请求应答存证
        case ACTION.InterfaceReqAckProof.reqAckProof => reqAckProof(ctx, json.extract[ReqAckProof])
        // 未匹配到的
        case _ => throw ContractException("no such method")
      }
    } catch {
      case ex: MappingException => throw ContractException(ex.getMessage)
    }
  }
}
