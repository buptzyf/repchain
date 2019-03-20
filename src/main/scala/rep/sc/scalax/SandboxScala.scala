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
import rep.utils.Json4s._
import com.google.protobuf.ByteString
import org.json4s._
import rep.log.trace.{RepLogger,ModuleType}
import rep.sc.contract._

/**
 * @author c4w
 */
class SandboxScala(cid:ChaincodeId) extends Sandbox(cid){
  var cobj:IContract = null
  
  def doTransaction(t:Transaction,from:ActorRef, da:String, isForInvoke:Boolean):DoTransactionResult ={
    //上下文可获得交易
    //要么上一份给result，重新建一份
    shim.sr = ImpDataPreloadMgr.GetImpDataPreload(sTag, da)
    shim.mb = scala.collection.mutable.Map[String,Array[Byte]]()
    shim.ol = scala.collection.mutable.ListBuffer.empty[Oper]
   //构造和传入ctx
   val ctx = new ContractContext(shim,t)
    //如果执行中出现异常,返回异常
    try{
      val tx_cid = getTXCId(t)

      val r: ActionResult = t.`type` match {
        //如果cid对应的合约class不存在，根据code生成并加载该class
        case Transaction.Type.CHAINCODE_DEPLOY => 
          //当：1.已存在同样的cid; 2.coder并非本人; 否决部署请求
          val key_tx = WorldStateKeyPreFix+ tx_cid
          val key_code =  WorldStateKeyPreFix+ cid.chaincodeName  
          val coder = shim.sr.Get(key_code)
          
          //isForInvoke是恢复已部署的合约而不是新部署合约
          if(!isForInvoke && shim.sr.Get(key_tx) != null)
            ActionResult(-1, Some("存在重复的合约Id"))
          else if(coder!= null && !t.signature.get.certId.get.creditCode.equals(new String(coder))){
                ActionResult(-2, Some("合约只能由部署者升级更新"))
          }else{          
            //热加载code对应的class
            val code = t.para.spec.get.codePackage
            val clazz = Compiler.compilef(code,tx_cid)
            cobj = clazz.getConstructor().newInstance().asInstanceOf[IContract]
            //cobj = new ContractAssets()
            cobj.init(ctx)
            //deploy返回chancode.name
            //利用kv记住cid对应的txid,并增加kv操作日志,以便恢复deploy时能根据cid找到当时deploy的tx及其代码内容
            //如果是部署合约需要登记部署合约相关的信息，包括：合约内容等。
            if(!isForInvoke){
              val txid = ByteString.copyFromUtf8(t.id).toByteArray()
              shim.sr.Put(key_tx,txid)
              shim.ol.append(new Oper(key_tx, null, txid))
              
              //利用kv记住合约的开发者
              val coder =  ByteString.copyFromUtf8(t.signature.get.certId.get.creditCode).toByteArray()
              shim.sr.Put(key_code,coder)
              shim.ol.append(new Oper(key_code, null, coder))     
            }
            new ActionResult(1,None)
          }
         //新建class实例并执行合约,传参为json数据
          //TODO case  Transaction.Type.CHAINCODE_DESC 增加对合约描述的处理
        case  Transaction.Type.CHAINCODE_INVOKE =>
          //获得合约action
          /*if(cobj == null){
            val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
            val key_tx = WorldStateKeyPreFix+ tx_cid
            val tx_id = ByteString.copyFrom(dataaccess.Get(key_tx)).toStringUtf8() 
            val block = Block.parseFrom(dataaccess.getBlockByTxId(tx_id))
            val ts = block.transactions
            for(t <- ts){
              if(tx_id.equalsIgnoreCase(t.id)){
                val code = t.para.spec.get.codePackage
                val clazz = Compiler.compilef(code,tx_cid)
                cobj = clazz.getConstructor().newInstance().asInstanceOf[IContract]
                //cobj = new ContractAssets()
                cobj.init(ctx)
              }
            }
          }*/
          val ipt = t.para.ipt.get
          val action = ipt.function
          //获得传入参数
          val data = ipt.args
          cobj.onAction(ctx,action,data.head)
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