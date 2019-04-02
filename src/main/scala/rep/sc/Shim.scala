/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
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
 *
 */

package rep.sc

import com.fasterxml.jackson.core.Base64Variants
import akka.actor.ActorSystem
import rep.network.PeerHelper
import rep.network.tools.PeerExtension
import rep.protos.peer.{Transaction,OperLog}
import rep.storage.ImpDataPreload
import rep.utils.SerializeUtils
import rep.utils.SerializeUtils.deserialise
import rep.utils.SerializeUtils.serialise
import java.security.cert.CertificateFactory
import java.io.FileInputStream
import java.io.ByteArrayInputStream
import fastparse.utils.Base64
import java.io.StringReader
import java.security.cert.X509Certificate
import rep.storage.ImpDataAccess
import rep.crypto.cert.SignTool
import  _root_.com.google.protobuf.ByteString 

/** Shim伴生对象
 *  @author c4w
 * 
 */
object Shim {
  
  type Key = String  
  type Value = Array[Byte]


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
class Shim(system: ActorSystem, cName: String) {

  import Shim._
  import rep.storage.IdxPrefix._

  val PRE_SPLIT = "_"
  //本chaincode的 key前缀
  val pre_key = WorldStateKeyPreFix + cName + PRE_SPLIT
  //存储模块提供的system单例
  val pe = PeerExtension(system)
  //从交易传入, 内存中的worldState快照
  var sr:ImpDataPreload = null;
  //记录状态修改日志
  var ol = scala.collection.mutable.ListBuffer.empty[OperLog]
    
  def setVal(key: Key, value: Any):Unit ={
    setState(key, serialise(value))
  }
   def getVal(key: Key):Option[Any] ={
    Some(deserialise(getState(key)))
  }
 
  def setState(key: Key, value: Array[Byte]): Unit = {
    val pkey = pre_key + key
    val oldValue = get(pkey)
    sr.Put(pkey, value)
    //记录操作日志
    ol += new OperLog(key,ByteString.copyFrom(oldValue), ByteString.copyFrom(value))
  }

  private def get(key: Key): Array[Byte] = {
    sr.Get(key)
  }

  def getState(key: Key): Array[Byte] = {
    get(pre_key + key)
  }

  def getStateEx(cName:String, key: Key): Array[Byte] = {
    get(WorldStateKeyPreFix + cName + PRE_SPLIT + key)
  }
  
  //判断账号是否节点账号 TODO
  def bNodeCreditCode(credit_code: String) : Boolean ={
    SignTool.isNode4Credit(credit_code)
  }
}