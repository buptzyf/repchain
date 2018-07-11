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

import rep.sc.Shim.Oper
import rep.utils.Json4s._
import com.google.protobuf.ByteString
import org.json4s._


/**
 * @author c4w
 */
class SandboxJS(cid:String) extends Sandbox(cid){
 
  val sandbox= new ScriptEngineManager().getEngineByName("nashorn")
  sandbox.put("shim",shim)
  
  override def doTransaction(t:Transaction,from:ActorRef, da:String):DoTransactionResult ={
   val tm_start = System.currentTimeMillis()
    //上下文可获得交易
    sandbox.put("tx", t)
    //for test print sandbox id
    sandbox.put("addr_self", this.addr_self)
    sandbox.put("tx_account", t.cert.toStringUtf8)
    //每次执行脚本之前重置 
    //shim.reset() 由于DoTransactionResult依赖此两项,不能直接clear,要么clone一份给result,
    //要么上一份给result，重新建一份
    shim.sr = ImpDataPreloadMgr.GetImpDataPreload(sTag, da)
    shim.mb = scala.collection.mutable.Map[String,Array[Byte]]()
    shim.ol = scala.collection.mutable.ListBuffer.empty[Oper]
    //如果执行中出现异常,返回异常
    try{
      val cs = t.payload.get
      val cid = cs.chaincodeID.get.name
//      println(s"doTransaction  type:${t.`type`}  cid:$cid addr:$addr_self")
      val r:JValue = t.`type` match {
        //部署合约时执行整个合约脚本，驻留funcs
        case Transaction.Type.CHAINCODE_DEPLOY => 
          //执行并加载functions
          sandbox.eval(cs.codePackage.toStringUtf8())
          //deploy返回chancode.name
          //利用kv记住cid对应的txid,并增加kv操作日志
          val txid = ByteString.copyFromUtf8(t.txid).toByteArray()
          val key = WorldStateKeyPreFix+ cid
          shim.sr.Put(key,txid)
          //ol value改为byte array
          shim.ol.append(new Oper(key, null, txid))
          encodeJson(cid)
         //调用方法时只需要执行function
        case  Transaction.Type.CHAINCODE_INVOKE =>
          var tm_start1 = System.currentTimeMillis()  

          val r1 = sandbox.eval(cs.ctorMsg.get.function)
          //val r2 = r1.asInstanceOf[Any]
          val r2 = encodeJson(r1)
         
          val span1 = System.currentTimeMillis()-tm_start1
            println(s"****container span1:$span1")
            r2
          //需自行递归处理所有层次的ScriptObjectMirror 二则akka默认的消息java序列化无法处理ScriptObjectMirror
          //json.callMember("stringify", r1).asInstanceOf[Any].toJson;
      }
      //modify by jiangbuyun 20170802
      val mb = shim.sr.GetComputeMerkle4String//sr.GetComputeMerkle  //mh.computeWorldState4Byte()
      val mbstr = mb match {
        case null => None
        case _ => Option(mb)  //Option(BytesHex.bytes2hex(mb))
      }
      new DoTransactionResult(t,from, r, 
          mbstr,
         shim.ol.toList,shim.mb,None)
    }catch{
      case e: Exception => 
        shim.rollback        
        log.error(t.txid, e)
        //val e1 = new Exception(e.getMessage, e.getCause)
        //akka send 无法序列化原始异常,简化异常信息
        val e1 = new SandboxException(e.getMessage)
        new DoTransactionResult(t,from, null,
           None,
          shim.ol.toList,shim.mb, 
          Option(akka.actor.Status.Failure(e1)))           
    }finally{
      val span = System.currentTimeMillis()-tm_start
        logMsg(LOG_TYPE.INFO, Sandbox.log_prefix, s"Span doTransaction:$span", "")
    }
  }
}