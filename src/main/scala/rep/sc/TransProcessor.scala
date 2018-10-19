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

import akka.actor.{Actor, ActorRef, Props, actorRef2Scala}
import delight.nashornsandbox._
import rep.protos.peer._
import rep.sc.Sandbox._
import rep.sc.Shim.Oper
import rep.utils.{GlobalUtils, RepLogging, TimeUtils}

import rep.storage._
import rep.crypto.ShaDigest
import rep.crypto.BytesHex._
import _root_.com.google.protobuf.ByteString

import scala.concurrent.duration._
import scala.concurrent._
import akka.util.Timeout
import akka.pattern.ask
import java.lang.Exception

import rep.storage.IdxPrefix.WorldStateKeyPreFix
import rep.utils.SerializeUtils
import rep.api.rest.RestActor.loadTransaction
import rep.network.tools.PeerExtension
import rep.sc.js.SandboxJS
import rep.sc.scalax.SandboxScala

/** 伴生对象，预定义了交易处理的异常描述，传入消息的case类，以及静态方法
 *  @author c4w
 * 
 */

object TransProcessor {
  //交易处理异常信息预定义
  val ERR_DEPLOY_CODE = "deploy交易代码内容不允许为空"
  val ERR_INVOKE_CHAINCODEID_EMPTY = "非deploy交易必须指定chaincodeId"
  val ERR_INVOKE_CHAINCODE_NOT_EXIST = "调用的chainCode不存在"
  
  //下属actor的命名前缀
  val PRE_SUB_ACTOR = "sb_"
  val log_prefix = "Sandbox-TransProcessor~"
  
  /** 从api请求传入的 处理的预执行交易的输入消息
   *  @constructor 对交易简单封装
   *  @param t 需要预执行的交易
   */
  case class PreTransaction(t:Transaction)
  
  /** 从共识层传入的执行交易请求
   *  @constructor 根据待执行交易、来源actor指向、数据访问标示建立实例
   * 	@param t 待执行交易
   *  @param from 来源actor指向
   *  @param da 数据访问标示
   */
  case class DoTransaction(t:Transaction,from:ActorRef, da:String)
  
  /** 本消息用于从存储恢复合约对应的sandbox
   *  @constructor 根据待执行交易、来源actor指向、数据访问标示建立实例
   * 	@param t 待执行交易
   *  @param from 来源actor指向
   *  @param da 数据访问标示
   */
  case class DeployTransaction(t:Transaction, from:ActorRef, da:String)
 
  /** 根据传入参数返回actor的Props
   *  @param name actor的命名
   *  @param da 数据访问标示
   *  @param parent 父actor指向
   *  @return 可用于创建actor的Props
   */
  def props(name: String, da:String, parent: ActorRef): Props = Props(classOf[TransProcessor], name, da, parent)
  
  /** 根据合约的链码定义获得其唯一标示
   *  @param c 链码定义
   *  @return 链码id
   */
  def getChaincodeId(c: ChaincodeSpec): String={
    if(c.codePackage.toStringUtf8().trim().equals(""))
        throw new SandboxException(ERR_DEPLOY_CODE)
    ShaDigest.hashstr(c.codePackage.toByteArray())
  }
  
  /** 从部署合约的交易，获得其部署的合约的链码id
   *  @param t 交易对象
   *  @return 链码id
   */
  def getTXCId(t: Transaction): String = {
    val cs = t.payload.get
    val cname = cs.chaincodeID.get.name
     t.`type` match {
        case Transaction.Type.CHAINCODE_DEPLOY => 
          getChaincodeId(cs)
        case _ =>
         if(cname.trim().equals(""))
            throw new SandboxException(ERR_INVOKE_CHAINCODEID_EMPTY)
          cname
    }
  }  
}


/**负责调度合约容器的actor
 * @author c4w
 * @constructor 以actor名称、数据访问实例标示、父actor指向创建调度actor
 * @param name actor名称
 * @param da 数据访问实例标示
 * @param parent 父actor指向
 */
class TransProcessor(name: String, da:String, parent: ActorRef) extends Actor with RepLogging{
  import TransProcessor._
  
  //获得所属于的actor system
  val pe = PeerExtension(context.system)
  val sTag = pe.getSysTag
  //设置同步处理请求的最大等待时间
  implicit val timeout = Timeout(1000.seconds)
 
  /** 请求消息的调度处理
   *  
   */
  def receive = {
    //来自共识层的执行交易请求
    case  ti:DoTransaction => 
      val st = ti.t.`type`
      val sb_actor = getSandboxActor(ti.t,ti.from,ti.da)
       sb_actor ! ti
      
    //来自API的预执行交易的post提交  
    case m:PreTransaction =>
      val st = m.t.`type`
      try{
        //获得合约对应的actor容器
        val sb_actor = getSandboxActor(m.t,sender,m.t.txid)
        val future = sb_actor ? m
        //同步阻塞等待执行结果
        val result = Await.result(future, timeout.duration).asInstanceOf[DoTransactionResult]
        //向请求发送方返回执行结果
        sender ! result
      }catch{
        case e:Exception => 
           val r = new DoTransactionResult(m.t,null, null, null,null,null,
               Option(akka.actor.Status.Failure(e)))
           //向请求发送方返回包含执行异常的结果
           sender ! r
      }
      
    //收到合约容器的执行结果,向原始请求者返回执行结果
    case tr: Sandbox.DoTransactionResult => 
      //如果交易请求中包含了from,结果发回from,否则向父actor回送交易执行结果
      if(tr.from != null)
         tr.from ! tr   
      else if(parent!=null)
        parent ! tr      
  }
  
  /** 根据待处理交易，请求发送actor，数据访问实例标示获得用于处理合约的容器actor
   *  @param t 待处理交易
   *  @param from 请求发送方
   *  @param da 数据访问标示
   *  @return 合约容器的actor指向
   */
  def getSandboxActor(t: Transaction,from:ActorRef, da:String): ActorRef = {
    //如果已经有对应的actor实例，复用之，否则建实例,actor name加字母前缀
    val cid = getTXCId(t)
    val sn = PRE_SUB_ACTOR+cid
    //log.info(s"idstr:$sn")
    //如果已经有对应的actor实例，复用之，否则建实例,actor name加字母前缀
    val cref = context.child(sn)
    cref match {
      case Some(r) =>
        r
      case None =>
        if(t.`type` != Transaction.Type.CHAINCODE_DEPLOY ){
          println(s"${pe.getSysTag} do invoke")
          //尝试从持久化恢复,找到对应的Deploy交易，并先执行之
          val sr = ImpDataAccess.GetDataAccess(sTag)
          val tidVal =  sr.Get(WorldStateKeyPreFix+ cid)
          if(tidVal==null)
            throw new SandboxException(ERR_INVOKE_CHAINCODE_NOT_EXIST)
          val txId = SerializeUtils.deserialiseJson(tidVal).asInstanceOf[String]
          val tx_deploy = loadTransaction(sr, txId).get
          // acto新建之后需要从持久化恢复的chainCode
          // 根据tx_deploy的类型决定采用js合约容器或者scala合约容器          
          val actor = tx_deploy.payload.get.ctype match{
            case rep.protos.peer.ChaincodeSpec.CodeType.CODE_SCALA => 
              context.actorOf(Props(new SandboxScala(cid)), sn)
            //默认采用jdk内置的javascript作为合约容器
            case _ => context.actorOf(Props(new SandboxJS(cid)), sn)
          }
          actor ! DeployTransaction(tx_deploy, from, da)
          actor
        }else
          //新执行的deploy交易,新建actor
          t.payload.get.ctype match{
            case rep.protos.peer.ChaincodeSpec.CodeType.CODE_SCALA => 
              context.actorOf(Props(new SandboxScala(cid)), sn)
            //默认采用jdk内置的javascript作为合约容器
            case _ => context.actorOf(Props(new SandboxJS(cid)), sn)
          }
    }
  }
}