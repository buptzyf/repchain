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

import akka.actor.{Actor, ActorRef, Props, actorRef2Scala}
import rep.utils._
import rep.api.rest._
import rep.protos.peer._
import delight.nashornsandbox._
import java.util.concurrent.Executors
import java.lang.Exception
import java.lang.Thread._
import java.io.File._
import rep.log.trace.LogType
import org.slf4j.LoggerFactory
import org.json4s.{DefaultFormats, Formats, jackson}
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s._
import com.trueaccord.scalapb.json.JsonFormat
import akka.util.Timeout
import Shim._
import rep.crypto.BytesHex
import rep.network.tools.PeerExtension
import rep.storage.IdxPrefix.WorldStateKeyPreFix
import rep.storage._
import rep.utils.SerializeUtils.deserialise
import rep.utils.SerializeUtils.serialise

/** 合约容器的抽象类伴生对象,定义了交易执行结果的case类
 * 
 * @author c4w
 * 
 */
object Sandbox {
  val SplitChainCodeId = "_"
  //日志前缀
  val log_prefix = "Sandbox-"
  //t:中含txid可以找到原始交易; r:执行结果; merkle:执行完worldstate的hash; err:执行异常
  /** 交易执行结果类
   * @param t 传入交易实例
   * @param from 来源actor指向
   * @param r 执行结果,任意类型
   * @param merkle 交易执行后worldState的merkle结果，用于验证和达成输出共识
   * @param ol 合约执行中对worldState的写入操作
   * @param mb 合约执行涉及的key-value集合
   * @param err 执行中抛出的异常信息
   */
  case class DoTransactionResult(txId:String,from:ActorRef, r:ActionResult,merkle:Option[String],
    ol:List[Oper],
    err:Option[akka.actor.Status.Failure])
    
  /** 合约执行异常类
   *  @param message 异常信息的文本描述
   *  @param cause 导致异常的原因
   */
  case class SandboxException(private val message: String = "", 
                           private val cause: Throwable = None.orNull)
                      extends Exception(message, cause) 

  /** 根据合约的链码定义获得其唯一标示
   *  @param c 链码Id
   *  @return 链码id字符串
   */
  def getChaincodeId(c: ChaincodeId): String={
    IdTool.getCid(c)
  }  
  /** 从部署合约的交易，获得其部署的合约的链码id
   *  @param t 交易对象
   *  @return 链码id
   */
  def getTXCId(t: Transaction): String = {
    val t_cid = t.cid.get
    getChaincodeId(t_cid)
  }  
}

/** 合约容器的抽象类，提供与底层进行API交互的shim实例，用于与存储交互的实例pe
 * @author c4w
 * 目前已实现的合约容器包括SandboxJS—以javascript作为合约脚本语言,不支持debug,性能较低;
 * 另一个实现是以scala作为合约脚本语言的SandboxScalax，支持debug,性能较高
 * 
 * @constructor 以合约在区块链上的链码id作为合约容器id建立实例
 * @param cid 链码id
 */
abstract class Sandbox(cid:ChaincodeId) extends Actor {
  import TransProcessor._
  import Sandbox._
  import spray.json._
  protected def log = LoggerFactory.getLogger(this.getClass)
  //与存储交互的实例
  val pe = PeerExtension(context.system)
  val sTag =pe.getSysTag
   

  //与底层交互的api实例,不同版本的合约KV空间重叠
  val shim = new Shim(context.system, cid.chaincodeName)
  val addr_self = akka.serialization.Serialization.serializedActorPath(self)

  def errAction(errCode: Int) :ActionResult = {
     errCode match{
       case -101 =>
         ActionResult(errCode,"目标合约不存在")
     }
     ActionResult(errCode,"不明原因")
  }
  /** 消息处理主流程,包括对交易处理请求、交易的预执行处理请求、从存储恢复合约的请求
   * 
   */
  def receive = {
    //交易处理请求
    case  DoTransaction(t:Transaction,from:ActorRef, da:String) =>
      val tr = onTransaction(t,from,da)
      sender ! tr
    //交易预处理请求，指定接收者
    case  PreTransaction(t:Transaction) =>
      val tr = onTransaction(t,null,t.id)
      sender ! tr
    //恢复chainCode,不回消息
    case  DeployTransaction(t:Transaction,from:ActorRef, da:String) =>
      val tr = onTransaction(t,from,da,true)
  }

  def onTransaction(t:Transaction,from:ActorRef, da:String, bRestore:Boolean=false):DoTransactionResult = {
    try{
          //要么上一份给result，重新建一份
      shim.sr = ImpDataPreloadMgr.GetImpDataPreload(sTag, da)
      checkTransaction(t, bRestore)
      shim.mb = scala.collection.mutable.Map[String,Option[Array[Byte]]]()
      shim.ol = new scala.collection.mutable.ListBuffer[Oper]
      doTransaction(t,from,da,bRestore)
    }catch{
        case e:Exception => 
          log.error(t.id, e)
          new DoTransactionResult(t.id,null, null, null,null,
               Option(akka.actor.Status.Failure(e)))
      }
  }
  
  
  /** 交易处理抽象方法，接受待处理交易，返回处理结果
   *  @param t 待处理交易
   *  @param from 发出交易请求的actor
   * 	@param da 存储访问标示
   *  @return 交易执行结果
   */
  def doTransaction(t:Transaction,from:ActorRef, da:String, bRestore:Boolean=false):DoTransactionResult 

   def checkTransaction(t: Transaction, bRestore:Boolean=false) = {
    val tx_cid = getTXCId(t)
    val sr = shim.sr
    t.`type`  match {
      case Transaction.Type.CHAINCODE_INVOKE =>
        //cid不存在或状态为禁用抛出异常
        val key_tx_state = WorldStateKeyPreFix+ tx_cid + PRE_STATE        
        val state_bytes = sr.Get(key_tx_state)
        //合约不存在
        if(state_bytes == null){
            throw new SandboxException(ERR_INVOKE_CHAINCODE_NOT_EXIST)    
        }
        else{
           val state = deserialise(state_bytes).asInstanceOf[Boolean]
           if(!state){
             throw new SandboxException(ERR_DISABLE_CID)    
           }
        }
      case Transaction.Type.CHAINCODE_DEPLOY =>
      //检查合约名+版本是否已存在,API预执行导致sandbox实例化，紧接着共识预执行
        val key_tx_state = WorldStateKeyPreFix+ tx_cid + PRE_STATE
        val state_bytes = sr.Get(key_tx_state)
        //合约已存在且并非恢复合约
        if(state_bytes != null && !bRestore){
           throw new SandboxException(ERR_REPEATED_CID)    
        }
       //检查合约部署者
       val cn = t.cid.get.chaincodeName
       val key_coder =  WorldStateKeyPreFix+ cn  
       val coder_bytes = sr.Get(key_coder)
        if(coder_bytes != null){
          val coder = Some(deserialise(coder_bytes).asInstanceOf[String])
          //合约已存在且部署者并非当前交易签名者
          if(!t.signature.get.certId.get.creditCode.equals(coder.get))
            throw new SandboxException(ERR_CODER)      
        }
        
      case Transaction.Type.CHAINCODE_SET_STATE =>
        val cn = t.cid.get.chaincodeName
        val key_coder =  WorldStateKeyPreFix+ cn  
        val coder_bytes = sr.Get(key_coder)
        if(coder_bytes != null){
          val coder = Some(deserialise(coder_bytes).asInstanceOf[String])
          //合约已存在且部署者并非当前交易签名者
          println(s"cn:${key_coder} :: ${t.signature.get.certId.get.creditCode} :: ${coder.get}")
          if(!t.signature.get.certId.get.creditCode.equals(coder.get)){
            throw new SandboxException(ERR_CODER)       
          }
        }
      //cid不存在抛出异常
        val key_tx_state = WorldStateKeyPreFix+ tx_cid + PRE_STATE
        val state_bytes = sr.Get(key_tx_state)
        //合约不存在
        if(state_bytes == null){
            throw new SandboxException(ERR_INVOKE_CHAINCODE_NOT_EXIST)    
        }
    }
 }  
}