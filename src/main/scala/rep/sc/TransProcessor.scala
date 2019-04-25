/*
 * Copyright  2019 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
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
import delight.nashornsandbox._
import rep.protos.peer._
import rep.sc.Sandbox._
import rep.utils.{GlobalUtils,  TimeUtils}

import rep.storage._

import scala.concurrent.duration._
import scala.concurrent._
import akka.util.Timeout
import akka.pattern.ask
import java.lang.Exception

import rep.storage.IdxPrefix.WorldStateKeyPreFix
import rep.utils.SerializeUtils
import rep.api.rest.RestActor.loadTransaction
import rep.network.tools.PeerExtension
import rep.sc.scalax.SandboxScala
import rep.utils.SerializeUtils.deserialise
import rep.utils.SerializeUtils.serialise
import org.slf4j.LoggerFactory

/** 伴生对象，预定义了交易处理的异常描述，传入消息的case类，以及静态方法
 *  @author c4w
 * 
 */

object TransProcessor {
  //交易处理异常信息预定义
  val ERR_DEPLOY_CODE = "deploy交易代码内容不允许为空"
  val ERR_INVOKE_CHAINCODEID_EMPTY = "非deploy交易必须指定chaincodeId"
  val ERR_INVOKE_CHAINCODE_NOT_EXIST = "调用的chainCode不存在"
  val ERR_REPEATED_CID ="存在重复的合约Id"
  val ERR_CODER = "合约只能由部署者升级更新"
  val ERR_DISABLE_CID ="合约处于禁用状态"
  //下属actor的命名前缀
  val PRE_SUB_ACTOR = "sb_"
  val log_prefix = "Sandbox-TransProcessor~"
  val PRE_STATE = "_STATE"
  
  /** 从api请求传入的 处理的预执行交易的输入消息
   *  @constructor 对交易简单封装
   *  @param t 需要预执行的交易
   */
  
  /** 从共识层传入的执行交易请求
   *  @constructor 根据待执行交易、来源actor指向、数据访问标示建立实例
   * 	@param t 待执行交易
   *  @param from 来源actor指向
   *  @param da 数据访问标示
   */
  final case class DoTransaction(t:Transaction,da:String)
  
  /** 本消息用于从存储恢复合约对应的sandbox
   *  @constructor 根据待执行交易、来源actor指向、数据访问标示建立实例
   * 	@param t 待执行交易
   *  @param from 来源actor指向
   *  @param da 数据访问标示
   */
  final case class DeployTransaction(t:Transaction, da:String)
 
  /** 根据传入参数返回actor的Props
   *  @param name actor的命名
   *  @param da 数据访问标示
   *  @param parent 父actor指向
   *  @return 可用于创建actor的Props
   */
  def props(name: String): Props = Props(classOf[TransProcessor], name)
  
}


/**负责调度合约容器的actor
 * @author c4w
 * @constructor 以actor名称、数据访问实例标示、父actor指向创建调度actor
 * @param name actor名称
 * @param da 数据访问实例标示
 * @param parent 父actor指向
 */
class TransProcessor(name: String) extends Actor {
  import TransProcessor._
  
   protected def log = LoggerFactory.getLogger(this.getClass)
  //获得所属于的actor system
  val pe = PeerExtension(context.system)
  val sTag = pe.getSysTag
  //设置同步处理请求的最大等待时间
  implicit val timeout = Timeout(1000.seconds)
 
  private def doSandbox(dotrans : DoTransaction)={
      try{
        //获得合约对应的actor容器
        val sb_actor = getSandboxActor(dotrans.t,dotrans.da)
        val future = sb_actor ? dotrans
        //同步阻塞等待执行结果
        val result = Await.result(future, timeout.duration).asInstanceOf[DoTransactionResult]
        //向请求发送方返回执行结果
        sender ! result
      }catch{
        case e:Exception => 
           val r = new DoTransactionResult(dotrans.t.id, null, null,
               Option(akka.actor.Status.Failure(e)))
           //向请求发送方返回包含执行异常的结果
           sender ! r
      }
  }
  
  /** 请求消息的调度处理
   *  
   */
  def receive = {
    //来自共识层的执行交易请求
    case  ti:DoTransaction => 
       doSandbox(DoTransaction(ti.t,ti.da))
  }
  
  def createActorByType(ctype: rep.protos.peer.ChaincodeDeploy.CodeType,
      cid:rep.protos.peer.ChaincodeId, sn:String): ActorRef = {
      ctype match{
        case rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA |
        rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA_PARALLEL=> 
          context.actorOf(Props(new SandboxScala(cid)), sn)
        //默认采用jdk内置的javascript作为合约容器
        case _ => context.actorOf(Props(new SandboxScala(cid)), sn)
      }    
  }
  /** 根据待处理交易，请求发送actor，数据访问实例标示获得用于处理合约的容器actor
   *  @param t 待处理交易
   *  @param from 请求发送方
   *  @param da 数据访问标示
   *  @return 合约容器的actor指向
   */
  def getSandboxActor(t: Transaction, da:String): ActorRef = {
    //如果已经有对应的actor实例，复用之，否则建实例,actor name加字母前缀
    val tx_cid = getTXCId(t)
    val cid = t.cid.get
    //检查交易是否有效，无效抛出异常
    //deploy之后紧接着调用同合约的invoke,会导致检查失败
    //checkTransaction(t)
    
    val sn = PRE_SUB_ACTOR+tx_cid
    //如果已经有对应的actor实例，复用之，否则建实例,actor name加字母前缀
    val cref = context.child(sn)
    cref match {
      case Some(r) =>
        //检查该合约是否被禁用
        r
      case None =>
        t.`type`  match {
          case Transaction.Type.CHAINCODE_INVOKE | Transaction.Type.CHAINCODE_SET_STATE=>
            //尝试从持久化恢复,找到对应的Deploy交易，并先执行之 
            val sr = ImpDataAccess.GetDataAccess(sTag)
            val key_tx = WorldStateKeyPreFix+ tx_cid
            val deploy_tx_id =sr.Get(key_tx) 
            if(deploy_tx_id==null)
              throw new SandboxException(ERR_INVOKE_CHAINCODE_NOT_EXIST)            
            val tx_deploy = loadTransaction(sr, deserialise(deploy_tx_id).asInstanceOf[String]).get
            // acto新建之后需要从持久化恢复的chainCode
            // 根据tx_deploy的类型决定采用js合约容器或者scala合约容器          
            val actor = createActorByType(  tx_deploy.para.spec.get.ctype, cid, sn)
            actor ! DeployTransaction(tx_deploy,  da)
            actor
          case Transaction.Type.CHAINCODE_DEPLOY =>
            //新执行的deploy交易,新建actor
            createActorByType(  t.para.spec.get.ctype, cid, sn)
          case _ => throw SandboxException(ERR_UNKNOWN_TRANSACTION_TYPE)
        }
    }
  }
  

}