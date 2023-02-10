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

import rep.api.rest.ResultCode
import rep.log.RepLogger
import rep.log.httplog.AlertInfo
import rep.proto.rc2.{ActionResult, ChaincodeId, Transaction, TransactionResult}
import rep.sc.Sandbox
import rep.sc.Sandbox._
import rep.sc.SandboxDispatcher.{DoTransactionOfSandboxInSingle, ERR_INVOKE_CHAINCODE_NOT_EXIST}
import rep.storage.chain.KeyPrefixManager
import rep.utils.IdTool

/**
 * @author c4w
 */
class SandboxScala(cid: ChaincodeId) extends Sandbox(cid) {
  var cobj: IContract = null

  private def LoadClass(ctx: ContractContext, txcid: String, t: Transaction) = {
    val code = t.para.spec.get.codePackage
    val clazz = Compiler.compilef(code, txcid,pe.getRepChainContext.getConfig,pe.getRepChainContext.getHashTool)
    //val clazz = CompilerOfSourceCode.compilef(code, txcid)  
    try{
      cobj = clazz.getConstructor().newInstance().asInstanceOf[IContract]
    }catch{
      case e:Exception =>cobj = clazz.newInstance().asInstanceOf[IContract]
    }
    
    cobj.init(ctx)
  }

  def doTransaction(dotrans: DoTransactionOfSandboxInSingle): TransactionResult = {
    //上下文可获得交易
    //构造和传入ctx
    val t = dotrans.t
    val da = dotrans.da
    val ctx = new ContractContext(shim, t)
    //System.err.println(s"nodename=${pe.getRepChainContext.getSystemName},txid=${t.id},before:"+shim.getStateGet.keySet.mkString("#"))
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
            //val tds = shim.getVal(tx_cid)
            val sr = pe.getRepChainContext.getBlockPreload(dotrans.da).getTransactionPreload(t.id)
            val pre = KeyPrefixManager.getWorldStateKeyPrefix(pe.getRepChainContext.getConfig, t.getCid.chaincodeName, t.oid) + SplitChainCodeId
            val tds = sr.getVal(pre+tx_cid)
            if (tds == null)
              throw new SandboxException(ERR_INVOKE_CHAINCODE_NOT_EXIST)
            val deployTransaction = pe.getRepChainContext.getBlockSearch.getTransactionByTxId(tds.toString)
            if(deployTransaction == None)
              throw new SandboxException(ERR_INVOKE_CHAINCODE_NOT_EXIST)
            this.LoadClass(ctx, tx_cid, deployTransaction.get)
          }
          val ipt = t.para.ipt.get
          val action = ipt.function
          //获得传入参数
          val data = ipt.args
          this.ExecutionInTimeoutManagement(timeout)(cobj.onAction(ctx, action, data.head))
        case Transaction.Type.CHAINCODE_SET_STATE =>
          val key_tx_state = tx_cid + PRE_STATE//KeyPrefixManager.getWorldStateKey(pe.getRepChainContext.getConfig,tx_cid+PRE_STATE,t.getCid.chaincodeName,t.oid)
          shim.setVal(key_tx_state,t.para.state.get)
          this.ContractStatus = Some(t.para.state.get)
          this.ContractStatusSource = Some(2)
          null
        case _ => throw SandboxException(ERR_UNKNOWN_TRANSACTION_TYPE)
      }
      pe.getRepChainContext.getBlockPreload(dotrans.da).getTransactionPreload(dotrans.t.id).commit
      //shim.srOfTransaction.commit
      //System.err.println(s"nodename=${pe.getRepChainContext.getSystemName},txid=${t.id},after:"+shim.getStateGet.keySet.mkString("#"))
      if(r == null){
        new TransactionResult(t.id, shim.getStateGet,shim.getStateSet,shim.getStateDel,Option(new ActionResult(ResultCode.Sandbox_Success,"")))
      }else {
        if(r.code == 0){
          new TransactionResult(t.id, shim.getStateGet,shim.getStateSet,shim.getStateDel,
            Option(new ActionResult(ResultCode.Sandbox_Success,"")))
        }else{
          pe.getRepChainContext.getBlockPreload(dotrans.da).getTransactionPreload(dotrans.t.id).rollback
          RepLogger.sendAlertToDB(pe.getRepChainContext.getHttpLogger(), new AlertInfo("CONTRACT", 4, s"Node Name=${pe.getSysTag},txid=${t.id},erroInfo=${r.reason},Transaction Exception."))
          new TransactionResult(t.id, Map.empty,Map.empty,Map.empty, Option(r))
        }
      }
    } catch {
      case e: Throwable =>
        RepLogger.except4Throwable(RepLogger.Sandbox_Logger, t.id, e)
        //akka send 无法序列化原始异常,简化异常信息
        val e1 = new SandboxException(e.getMessage)
        RepLogger.sendAlertToDB(pe.getRepChainContext.getHttpLogger(),new AlertInfo("CONTRACT",4,s"Node Name=${pe.getSysTag},txid=${t.id},erroInfo=${e.getMessage},Transaction Exception."))
        //shim.srOfTransaction.rollback
        pe.getRepChainContext.getBlockPreload(dotrans.da).getTransactionPreload(dotrans.t.id).rollback
        new TransactionResult(t.id, Map.empty,Map.empty,Map.empty,Option(ActionResult(ResultCode.Transaction_Exception_In_SandboxOfScala,e1.getMessage)))
    }
  }
}