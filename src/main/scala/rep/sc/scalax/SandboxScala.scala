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

package rep.sc.scalax
import rep.sc.Sandbox
import rep.sc.Sandbox._
import javax.script._
import java.security.cert.Certificate
import jdk.nashorn.api.scripting._
import rep.protos.peer._
import akka.actor.{ Actor, ActorRef, Props, actorRef2Scala }
import rep.storage._
import rep.storage.IdxPrefix.WorldStateKeyPreFix
import org.slf4j.LoggerFactory
import org.json4s._
import rep.log.RepLogger
import rep.utils.SerializeUtils.deserialise
import rep.utils.SerializeUtils.serialise
import _root_.com.google.protobuf.ByteString
import rep.utils.IdTool
import rep.sc.SandboxDispatcher.DoTransactionOfSandbox
import rep.protos.peer.Transaction
import rep.sc.SandboxDispatcher.ERR_INVOKE_CHAINCODE_NOT_EXIST

/**
 * @author c4w
 */
class SandboxScala(cid: ChaincodeId) extends Sandbox(cid) {
  var cobj: IContract = null
  val PRE_STATE = "_STATE"

  private def LoadClass(ctx: ContractContext, txcid: String, t: Transaction) = {
    val code = t.para.spec.get.codePackage
    val clazz = Compiler.compilef(code, txcid)  
    //val clazz = CompilerOfSourceCode.compilef(code, txcid)  
    try{
      cobj = clazz.getConstructor().newInstance().asInstanceOf[IContract]
    }catch{
      case e:Exception =>cobj = clazz.newInstance().asInstanceOf[IContract]
    }
    
    cobj.init(ctx)
  }

  private def DoDeploy(tx_cid: String, t: Transaction) = {
    //deploy返回chancode.name
    //新部署合约利用kv记住cid对应的txid,并增加kv操作日志,以便恢复deploy时能根据cid找到当时deploy的tx及其代码内容
    //部署合法性在外围TransProcessor中检查
    val key_tx = WorldStateKeyPreFix + tx_cid
    val cn = cid.chaincodeName
    val key_coder = WorldStateKeyPreFix + cn
    val coder = t.signature.get.certId.get.creditCode
    val txid = serialise(t.id)
    shim.sr.Put(key_tx, txid)
    shim.ol.append(OperLog(key_tx, ByteString.EMPTY, ByteString.copyFrom(txid)))

    //写入初始状态
    val key_tx_state = WorldStateKeyPreFix + tx_cid + PRE_STATE
    val state_enable = serialise(true)
    shim.sr.Put(key_tx_state, state_enable)
    shim.ol.append(OperLog(key_tx_state, ByteString.EMPTY, ByteString.copyFrom(state_enable)))

    //利用kv记住合约的开发者
    val coder_bytes = serialise(coder)
    shim.sr.Put(key_coder, coder_bytes)
    shim.ol.append(OperLog(key_coder, ByteString.EMPTY, ByteString.copyFrom(coder_bytes)))
  }

  def doTransaction(dotrans: DoTransactionOfSandbox): DoTransactionResult = {
    //上下文可获得交易
    //构造和传入ctx
    val t = dotrans.t
    val da = dotrans.da
    val ctx = new ContractContext(shim, t)
    //如果执行中出现异常,返回异常
    try {
      val tx_cid = IdTool.getTXCId(t)
      val r: ActionResult = t.`type` match {
        //如果cid对应的合约class不存在，根据code生成并加载该class
        case Transaction.Type.CHAINCODE_DEPLOY =>
          //热加载code对应的class
          LoadClass(ctx, tx_cid, t)
          DoDeploy(tx_cid, t)
          null
        //由于Invoke为最频繁调用，因此应尽量避免在处理中I/O读写,比如合约状态的检查就最好在内存中处理
        //TODO case  Transaction.Type.CHAINCODE_DESC 增加对合约描述的处理
        case Transaction.Type.CHAINCODE_INVOKE =>
          if (this.cobj == null) {
            val db = ImpDataAccess.GetDataAccess(pe.getSysTag)
            val tds = db.getTransOfContractFromChaincodeId(WorldStateKeyPreFix +tx_cid)
            if (tds == None) throw new SandboxException(ERR_INVOKE_CHAINCODE_NOT_EXIST)
            this.LoadClass(ctx, tx_cid, tds.get)
          }
          val ipt = t.para.ipt.get
          val action = ipt.function
          //获得传入参数
          val data = ipt.args
          //由于本actor的顺序一定是先收到CHAINCODE_DEPLOY 再收到CHAINCODE_INVOKE所以 cobj一定已经实例化了
          cobj.onAction(ctx, action, data.head)
        case Transaction.Type.CHAINCODE_SET_STATE =>
          val key_tx_state = WorldStateKeyPreFix + tx_cid + PRE_STATE
          val state = t.para.state.get
          val state_bytes = serialise(state)
          
          val oldstate = shim.sr.Get(key_tx_state)
          var oldbytestring = ByteString.EMPTY
          if(oldstate != null){
            oldbytestring = ByteString.copyFrom(state_bytes)
          }
          
          shim.sr.Put(key_tx_state, state_bytes)
          shim.ol.append(OperLog(key_tx_state, oldbytestring, ByteString.copyFrom(state_bytes)))
          null
        case _ => throw SandboxException(ERR_UNKNOWN_TRANSACTION_TYPE)
      }
      new DoTransactionResult(t.id, r, shim.ol.toList, None)
    } catch {
      case e: Throwable =>
        RepLogger.except4Throwable(RepLogger.Sandbox_Logger, t.id, e)
        //akka send 无法序列化原始异常,简化异常信息
        val e1 = new SandboxException(e.getMessage)
        new DoTransactionResult(t.id, null,
          shim.ol.toList,
          Option(akka.actor.Status.Failure(e1)))
    }
  }
}