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

package rep.sc.scalax
import rep.sc.Sandbox
import rep.sc.Sandbox._
import javax.script._
import java.security.cert.Certificate
import jdk.nashorn.api.scripting._
import rep.protos.peer._
import akka.actor.{Actor, ActorRef, Props, actorRef2Scala}
import rep.storage._
import rep.storage.IdxPrefix.WorldStateKeyPreFix
import rep.log.trace.LogType
import org.slf4j.LoggerFactory
import rep.sc.Shim.Oper
import org.json4s._
import rep.log.trace.{RepLogger,ModuleType}
import rep.utils.SerializeUtils.deserialise
import rep.utils.SerializeUtils.serialise

/**
 * @author c4w
 */
class SandboxScala(cid:ChaincodeId) extends Sandbox(cid){
  var cobj:IContract = null
  val PRE_STATE = "_STATE"
  
  def doTransaction(t:Transaction,from:ActorRef, da:String, bRestore:Boolean):DoTransactionResult ={
    //上下文可获得交易
   //构造和传入ctx
   val ctx = new ContractContext(shim,t)
    //如果执行中出现异常,返回异常
    try{
      val tx_cid = getTXCId(t)
      val r: ActionResult = t.`type` match {
        //如果cid对应的合约class不存在，根据code生成并加载该class
        case Transaction.Type.CHAINCODE_DEPLOY => 
          //部署合法性在外围TransProcessor中检查
          val key_tx = WorldStateKeyPreFix+ tx_cid
          val cn = cid.chaincodeName
          val key_coder =  WorldStateKeyPreFix+ cn
           //热加载code对应的class
          val code = t.para.spec.get.codePackage
          val clazz = Compiler.compilef(code,tx_cid)
          cobj = clazz.getConstructor().newInstance().asInstanceOf[IContract]
          cobj.init(ctx)
          //deploy返回chancode.name
          //新部署合约利用kv记住cid对应的txid,并增加kv操作日志,以便恢复deploy时能根据cid找到当时deploy的tx及其代码内容
          val coder = t.signature.get.certId.get.creditCode
          if(!bRestore){
            val txid = serialise(t.id)
            shim.sr.Put(key_tx,txid)
            shim.ol.append(new Oper(key_tx, null, Some(txid)))
            
            //写入初始状态
            val key_tx_state = WorldStateKeyPreFix+ tx_cid + PRE_STATE
            val state_enable = serialise(true)
            shim.sr.Put(key_tx_state,state_enable)
            shim.ol.append(new Oper(key_tx_state, null, Some(state_enable)))
            
            //利用kv记住合约的开发者
            val coder_bytes =  serialise(coder)
            shim.sr.Put(key_coder,coder_bytes)
            shim.ol.append(new Oper(key_coder, null, Some(coder_bytes)))            
          }     
          new ActionResult(1,None)
         //由于Invoke为最频繁调用，因此应尽量避免在处理中I/O读写,比如合约状态的检查就最好在内存中处理
          //TODO case  Transaction.Type.CHAINCODE_DESC 增加对合约描述的处理
        case  Transaction.Type.CHAINCODE_INVOKE =>
          val ipt = t.para.ipt.get
          val action = ipt.function
          //获得传入参数
          val data = ipt.args
         //由于本actor的顺序一定是先收到CHAINCODE_DEPLOY 再收到CHAINCODE_INVOKE所以 cobj一定已经实例化了
          cobj.onAction(ctx,action,data.head)
        case  Transaction.Type.CHAINCODE_SET_STATE =>
          val key_tx_state = WorldStateKeyPreFix+ tx_cid + PRE_STATE
          val state = t.para.state.get
          val state_bytes = serialise(state)
          shim.sr.Put(key_tx_state,state_bytes)
          shim.ol.append(new Oper(key_tx_state, null, Some(state_bytes)))
          new ActionResult(1,None)
      }
      val mb = shim.sr.GetComputeMerkle4String
      val mbstr = mb match {
        case null => None
        case _ => Option(mb)  //Option(BytesHex.bytes2hex(mb))
      }
      new DoTransactionResult(t,from, r, 
          mbstr,
         shim.ol.toList,shim.mb,None)
    }catch{
      case e: Exception => 
        log.error(t.id, e)
        //akka send 无法序列化原始异常,简化异常信息
        val e1 = new SandboxException(e.getMessage)
        new DoTransactionResult(t,from, null,
           None,
          shim.ol.toList,shim.mb, 
          Option(akka.actor.Status.Failure(e1)))           
    }
  }  
}