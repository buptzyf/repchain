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

package rep.sc.js
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
import rep.utils.Json4s._
import com.google.protobuf.ByteString
import org.json4s._
import rep.log.trace.{RepLogger,ModuleType}
import rep.sc.contract.ActionResult

/**
 * @author c4w
 */
class SandboxJS(cid:ChaincodeId) extends Sandbox(cid){
 
  val sandbox= new ScriptEngineManager().getEngineByName("nashorn")
  sandbox.put("shim",shim)
  
  override def doTransaction(t:Transaction,from:ActorRef, da:String):DoTransactionResult ={
    //上下文可获得交易
    sandbox.put("tx", t)
    //for test print sandbox id
    sandbox.put("addr_self", this.addr_self)
    sandbox.put("tx_account", t.signature.get.certId.get.creditCode)
    //要么上一份给result，重新建一份
    shim.sr = ImpDataPreloadMgr.GetImpDataPreload(sTag, da)
    shim.mb = scala.collection.mutable.Map[String,Array[Byte]]()
    shim.ol = scala.collection.mutable.ListBuffer.empty[Oper]
    //如果执行中出现异常,返回异常
    try{
      val cs = t.para.spec.get.codePackage
      val cid = getTXCId(t)
      val r:ActionResult = t.`type` match {
        //部署合约时执行整个合约脚本，驻留funcs
        case Transaction.Type.CHAINCODE_DEPLOY => 
          //执行并加载functions
          sandbox.eval(cs)
          //deploy返回chancode.name
          //利用kv记住cid对应的txid,并增加kv操作日志
          val txid = ByteString.copyFromUtf8(t.id).toByteArray()
          val key = WorldStateKeyPreFix+ cid
          shim.sr.Put(key,txid)
          //ol value改为byte array
          shim.ol.append(new Oper(key, null, txid))
          new ActionResult(1,None)
         //调用方法时只需要执行function
        case  Transaction.Type.CHAINCODE_INVOKE =>
          val r1 = sandbox.eval(t.para.ipt.get.function)
          r1.asInstanceOf[ActionResult]
      }
      new DoTransactionResult(t,from, r, 
          None,
         shim.ol.toList,shim.mb,None)
    }catch{
      case e: Exception => 
        shim.rollback        
        log.error(t.id, e)
        
        //val e1 = new Exception(e.getMessage, e.getCause)
        //akka send 无法序列化原始异常,简化异常信息
        val e1 = new SandboxException(e.getMessage)
        new DoTransactionResult(t,from, null,
           None,
          shim.ol.toList,shim.mb, 
          Option(akka.actor.Status.Failure(e1)))           
    }
  }
}