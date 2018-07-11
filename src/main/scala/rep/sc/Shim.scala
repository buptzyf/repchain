/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Fintech Research Center of ISCAS.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BA SIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rep.sc

import java.security.cert.Certificate

import com.fasterxml.jackson.core.Base64Variants
import com.google.protobuf.ByteString

import akka.actor.ActorSystem
import rep.crypto.ECDSASign
import rep.network.PeerHelper
import rep.network.tools.PeerExtension
import rep.protos.peer.Transaction
import rep.storage.FakeStorage.Key
import rep.storage.ImpDataPreload
import rep.utils.SerializeUtils
import rep.utils.SerializeUtils.deserialiseJson
import rep.utils.SerializeUtils.serialiseJson
import java.security.cert.CertificateFactory
import java.io.FileInputStream
import java.io.ByteArrayInputStream
import fastparse.utils.Base64
import java.io.StringReader
import java.security.cert.X509Certificate
import rep.storage.ImpDataAccess


/** Shim伴生对象
 *  @author c4w
 * 
 */
object Shim {
  /** 用于记录合约对worldState的写入日志
   *  @param key 键名
   *  @param oldValue 旧键值
   *  @param newValue 新写入的键值
   */
  case class Oper(key: Key, oldValue: Array[Byte], newValue: Array[Byte])

  import rep.storage.IdxPrefix._
  val ERR_CERT_EXIST = "证书已存在"
  val PRE_CERT_INFO = "CERT_INFO_"
  val PRE_CERT = "CERT_"
  val NOT_PERR_CERT = "非节点证书"
//  case class Oper(key: Key, oldValue: Array[Byte], newValue: Array[Byte])
  
  
  def main(args: Array[String]): Unit = {
    val test = "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUJtakNDQVQrZ0F3SUJBZ0lFV1dWK0F6QUtCZ2dxaGtqT1BRUURBakJXTVFzd0NRWURWUVFHRXdKamJqRUxNQWtHQTFVRUNBd0NZbW94Q3pBSkJnTlZCQWNNQW1KcU1SRXdEd1lEVlFRS0RBaHlaWEJqYUdGcGJqRU9NQXdHQTFVRUN3d0ZhWE5qWVhNeENqQUlCZ05WQkFNTUFURXdIaGNOTVRjd056RXlNREUwTWpFMVdoY05NVGd3TnpFeU1ERTBNakUxV2pCV01Rc3dDUVlEVlFRR0V3SmpiakVMTUFrR0ExVUVDQXdDWW1veEN6QUpCZ05WQkFjTUFtSnFNUkV3RHdZRFZRUUtEQWh5WlhCamFHRnBiakVPTUF3R0ExVUVDd3dGYVhOallYTXhDakFJQmdOVkJBTU1BVEV3VmpBUUJnY3Foa2pPUFFJQkJnVXJnUVFBQ2dOQ0FBVDZWTEUvZUY5K3NLMVJPbjhuNng3aEtzQnhlaFc0MnFmMUlCOHF1Qm41T3JRRDN4Mkg0eVpWRHdQZ2NFVUNqSDhQY0Znc3dkdGJvOEpMLzdmNjZ5RUNNQW9HQ0NxR1NNNDlCQU1DQTBrQU1FWUNJUUN1ZCs0LzNuam5mVWtHOWZmU3FjSGhuc3VaTk1Rd2FXNjJFVlhiY2pvaUJnSWhBUG9MSksxRDA2SU1vaG9sWWNzZ1RRYjVUcnJlai9lclpPTk1tMWNTMWlQKwotLS0tLUVORCBDRVJUSUZJQ0FURS0tLS0t"
    val cf = CertificateFactory.getInstance("X.509");
    val cert = cf.generateCertificate(
      new ByteArrayInputStream(
        Base64.Decoder(test).toByteArray()
      )
    )
    val test1 = cert
    println(Option(null) == None)
  }
}

/** 为合约容器提供底层API的类
 *  @author c4w
 * @constructor 根据actor System和合约的链码id建立shim实例
 * @param system 所属的actorSystem
 * @param cid 合约的链码id
 */
class Shim(system: ActorSystem, cid: String) {

  import Shim._
  import rep.storage.IdxPrefix._

  //本chaincode的 key前缀
  val pre_key = WorldStateKeyPreFix + cid + "_"
  //存储模块提供的system单例
  val pe = PeerExtension(system)
  //从交易传入, 内存中的worldState快照
  var sr:ImpDataPreload = null;
  //记录初始state
  var mb = scala.collection.mutable.Map[Key, Array[Byte]]()
  //记录状态修改日志
  var ol = scala.collection.mutable.ListBuffer.empty[Oper]
  
  /**
    * 更新系统信任列表，添加证书
    *
    * @param cert 从Transaction中获取，需要用base58进行转化
    */
  def loadCert(cert:String): Unit = {
    //TODO 实现本地加载证书到信任证书列表，从而通过交易完成入网许可
    if(pe.getSysTag=="1"){
      ECDSASign.loadTrustedCertBase64(cert, "2") match {
        case true =>
          println("True")
        case false =>
          println("False")
      }
    }
  }

  def signup(pemcert:String, inf:String):String = {
//    val pemcertstr = new String (Base64.Decoder(pemcert).toByteArray)
    val cf = CertificateFactory.getInstance("X.509");
//    val subpemcert = pemcertstr.replaceAll("\r\n", "").stripPrefix("-----BEGIN CERTIFICATE-----").stripSuffix("-----END CERTIFICATE-----")
    val cert = cf.generateCertificate(
      new ByteArrayInputStream(
        Base64.Decoder(pemcert).toByteArray()
      )
    )
    val bdata = SerializeUtils.serialise(cert)
    //step2 取二进制数据数据的地址
    val addr = ECDSASign.getBitcoinAddrByCert(bdata)
    val key = PRE_CERT+addr
    val dert = getVal(key)
    if( dert!= null){
      val dert = new String(getState(key))
      println(dert)
      if(dert != "null") 
        throw new Exception(ERR_CERT_EXIST)
    }
    //保存证书
    setState(key,bdata)
    setVal(PRE_CERT_INFO+addr,inf)
    println("证书短地址： "+addr)
    addr
  }
  
  def signupback(cert:String,inf:String):String ={
    //step1 从base64编码字符串获得二进制数据
    val bdata = Base64Variants.getDefaultVariant.decode(cert)
//  TODO 把二进制的证书转换为明文，看一下,检查该证书是否非节点证书
    val certTx = SerializeUtils.deserialise(bdata).asInstanceOf[Certificate]
    val bitcoinaddr = ECDSASign.getBitcoinAddrByCert(certTx)
    val peercer = ECDSASign.getCertByBitcoinAddr(bitcoinaddr)
//    val addstr = (new code).encodeHex(bdata)
    
    //step2 取二进制数据数据的地址
    val addr = ECDSASign.getBitcoinAddrByCert(bdata)
    val key = PRE_CERT+addr
    val dert = getVal(key)
    if( dert!= null){
      throw new Exception(ERR_CERT_EXIST)
    }
    //保存证书
    setState(key,bdata)
    setVal(PRE_CERT_INFO+addr,inf)
    addr
  }
  
  
  def certAddr(cert:String):String = {
    //step1 从base64编码字符串获得二进制数据
    val bdata = Base64Variants.getDefaultVariant.decode(cert)
    //step2 取二进制数据数据的地址
    return ECDSASign.getBitcoinAddrByCert(bdata)
  }
  
  def setVal(key: Key, value: Any):Unit ={
    setState(key,serialiseJson(value))
  }
   def getVal(key: Key):Any ={
    deserialiseJson(getState(key))
  }
 
  def setState(key: Key, value: Array[Byte]): Unit = {
    val pkey = pre_key + key
    val oldValue = get(pkey)
    sr.Put(pkey, value)
    //记录初始值
    if (!mb.contains(pkey)) {
      mb.put(pkey, oldValue)
    }
    //记录操作日志
    ol += new Oper(key, oldValue, value)
  }

  private def get(key: Key): Array[Byte] = {
    sr.Get(key)
  }

  def getState(key: Key): Array[Byte] = {
    get(pre_key + key)
  }

  //禁止脚本内调用此方法, 上下文应严密屏蔽不必要的方法和变量
  private def reset() = {
    mb.clear()
    ol.clear()
  }

  //回滚到初始值
  def rollback() = {
    for ((k, v) <- mb) sr.Put(k, v)
    //回滚仍然保留操作日志,否则api拿不到操作日志
    // ol.clear()
  }
  
  // 进行是否为非节点签名验证
  def check(bitcoinaddr: String, tx: Transaction) : Unit = {
    val sig = tx.signature.toByteArray
    val tOutSig1 = tx.withSignature(ByteString.EMPTY)
    val tOutSig  = tOutSig1.withMetadata(ByteString.EMPTY)
    val peercer = ECDSASign.getCertByBitcoinAddr(bitcoinaddr)
    if (peercer == None)
      throw new Exception(NOT_PERR_CERT)
    ECDSASign.verify(sig, PeerHelper.getTxHash(tOutSig), peercer.get.getPublicKey) match {
      case true => 
      case false => throw new Exception("节点签名验证错误")
    }
  }
  
  /**
   * @author zyf
   * @param certAddr: 证书短地址
   * 
   */
  def destroyCert(certAddr: String): Unit = {
    //TODO 判断下下该证书是列表里的还是ws里的，然后如何销毁？  赋个空值？
    val key = PRE_CERT+certAddr
//    val pkey = pre_key + key
    val cert = Option(getVal(key))
    if (cert == None) {
      throw new Exception("不存在该用户证书")
    } else {
      val dert = new String(getState(key))
      if(dert == "null") 
        throw new Exception("该证书已经注销")
    }
    setState(key,"null".getBytes)  // 注销证书，置空
  }
  
  /**
   * @author zyf
   * @param pemcert: pem证书字符串
   * @param certAddr: 证书短地址
   */
  def replaceCert(pemCert: String, certAddr: String) :String = {
    val cf = CertificateFactory.getInstance("X.509");
    val cert = cf.generateCertificate(
      new ByteArrayInputStream(
        Base64.Decoder(pemCert).toByteArray()
      )
    )
    val bdata = SerializeUtils.serialise(cert)
    val key = PRE_CERT+certAddr
    setState(key,bdata)
    val addr = ECDSASign.getBitcoinAddrByCert(bdata)
    println(addr)
    addr
  }
  
}