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
import rep.log.trace.RepLogHelp
import rep.log.trace.LogType
import org.slf4j.LoggerFactory
import rep.sc.Shim.Oper
import rep.utils.Json4s._
import com.google.protobuf.ByteString
import org.json4s._

import rep.sc.contract._

/**
 * @author c4w
 */
class SandboxScala(cid:String) extends Sandbox(cid){
  var cobj:IContract = null
  val pcid = cid
  
  def doTransaction(t:Transaction,from:ActorRef, da:String):DoTransactionResult ={
   val tm_start = System.currentTimeMillis()
    //上下文可获得交易
    //每次执行脚本之前重置 
    //shim.reset() 由于DoTransactionResult依赖此两项,不能直接clear,要么clone一份给result,
    //要么上一份给result，重新建一份
    shim.sr = ImpDataPreloadMgr.GetImpDataPreload(sTag, da)
    shim.mb = scala.collection.mutable.Map[String,Array[Byte]]()
    shim.ol = scala.collection.mutable.ListBuffer.empty[Oper]
   //构造和传入ctx
   val ctx = new ContractContext(shim,t)
    //如果执行中出现异常,返回异常
    try{
      val cs = t.payload.get
      val cid = cs.chaincodeID.get.name
      val r:JValue = t.`type` match {
        //如果cid对应的合约class不存在，根据code生成并加载该class
        case Transaction.Type.CHAINCODE_DEPLOY => 
          //TODO 热加载code对应的class
          val code = cs.codePackage.toStringUtf8()

          val clazz = Compiler.compilef(code,pcid)
          cobj = clazz.getConstructor().newInstance().asInstanceOf[IContract]
          //cobj = new ContractAssets()
          cobj.init(ctx)
          //deploy返回chancode.name
          //利用kv记住cid对应的txid,并增加kv操作日志,以便恢复deploy时能根据cid找到当时deploy的tx及其代码内容
          val txid = ByteString.copyFromUtf8(t.txid).toByteArray()
          val key = WorldStateKeyPreFix+ cid
          shim.sr.Put(key,txid)
          //ol value改为byte array
          shim.ol.append(new Oper(key, null, txid))
          encodeJson(cid)
         //新建class实例并执行合约,传参为json数据
          //TODO case  Transaction.Type.CHAINCODE_DESC 增加对合约描述的处理
        case  Transaction.Type.CHAINCODE_INVOKE =>
          //获得合约action
          val action = cs.ctorMsg.get.function
          //获得传入参数
          val data = cs.ctorMsg.get.args
          encodeJson(cobj.onAction(ctx,action,data.head))
      }
      val span2 = System.currentTimeMillis()-tm_start
      //println(s"container span2:$span2")
      
      //modify by jiangbuyun 20170802
      //TODO 有必要每笔交易都计算Merkle根吗？？？
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
      RepLogHelp.logMsg(log,LogType.INFO,Sandbox.log_prefix+"~"+ s"Span doTransaction:$span")
       // logMsg(LOG_TYPE.INFO, Sandbox.log_prefix, s"Span doTransaction:$span", "")
    }
  }  
}