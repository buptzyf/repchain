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


import rep.log.RepLogger
import rep.log.httplog.AlertInfo
import rep.proto.rc2.{ActionResult, ChaincodeId, Transaction, TransactionResult}
import rep.sc.Sandbox
import rep.sc.Sandbox._
import rep.sc.SandboxDispatcher.{DoTransactionOfSandboxInSingle, ERR_INVOKE_CHAINCODE_NOT_EXIST}
import rep.storage.chain.KeyPrefixManager
import rep.utils.{IdTool}
import rep.utils.SerializeUtils.serialise

/**
 * @author c4w
 */
class SandboxScala(cid: ChaincodeId) extends Sandbox(cid) {
  var cobj: IContract = null
  val PRE_STATE = "_STATE"

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

  private def DoDeploy(tx_cid: String, t: Transaction) = {
    //deploy返回chancode.name
    //新部署合约利用kv记住cid对应的txid,并增加kv操作日志,以便恢复deploy时能根据cid找到当时deploy的tx及其代码内容
    //部署合法性在外围TransProcessor中检查
    val key_tx = tx_cid //KeyPrefixManager.getWorldStateKey(pe.getRepChainContext.getConfig,tx_cid,tx_cid,t.oid)
    val cn = cid.chaincodeName
    val key_coder = cn //KeyPrefixManager.getWorldStateKey(pe.getRepChainContext.getConfig,cn,tx_cid,t.oid)
    val coder = t.signature.get.certId.get.creditCode
    //val txId = serialise(t.id)
    shim.setVal(key_tx,t.id)
    //shim.srOfTransaction.put(key_tx, txId)
    //shim.stateSet += key_tx -> ByteString.copyFrom(txId)

    //写入初始状态
    val key_tx_state = tx_cid + PRE_STATE //KeyPrefixManager.getWorldStateKey(pe.getRepChainContext.getConfig,tx_cid + PRE_STATE,tx_cid,t.oid)
    //val state_enable = serialise(true)
    this.ContractStatus = Some(true)
    this.ContractStatusSource = Some(2)

    shim.setVal(key_tx_state,true)
    //shim.srOfTransaction.put(key_tx_state, state_enable)
    //shim.stateSet += key_tx_state -> ByteString.copyFrom(state_enable)


    //利用kv记住合约的开发者
    //val coder_bytes = serialise(coder)
    shim.setVal(key_coder,coder)
    //shim.srOfTransaction.put(key_coder, coder_bytes)
    //shim.stateSet += key_coder -> ByteString.copyFrom(coder_bytes)
  }

  def doTransaction(dotrans: DoTransactionOfSandboxInSingle): TransactionResult = {
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
            val tds = shim.getVal(tx_cid)
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
          cobj.onAction(ctx, action, data.head)
        case Transaction.Type.CHAINCODE_SET_STATE =>
          val key_tx_state = KeyPrefixManager.getWorldStateKey(pe.getRepChainContext.getConfig,tx_cid+PRE_STATE,t.getCid.chaincodeName,t.oid)
          shim.setVal(key_tx_state,t.para.state.get)
          //val state_bytes = serialise(t.para.state.get)
          //val oldState = shim.srOfTransaction.get(key_tx_state)
          //var oldByteString = if(oldState != null) ByteString.copyFrom(serialise(oldState.get))
          //shim.srOfTransaction.put(key_tx_state, state_bytes)
          //shim.stateGet += key_tx_state -> oldByteString
          //shim.stateSet += key_tx_state -> ByteString.copyFrom(state_bytes)

          this.ContractStatus = Some(t.para.state.get)
          this.ContractStatusSource = Some(2)
          null
        case _ => throw SandboxException(ERR_UNKNOWN_TRANSACTION_TYPE)
      }
      pe.getRepChainContext.getBlockPreload(dotrans.da).getTransactionPreload(dotrans.t.id).commit
      //shim.srOfTransaction.commit
      if(r == null){
        new TransactionResult(t.id, shim.getStateGet,shim.getStateSet,shim.getStateDel,Option(new ActionResult(0,"")))
      }else{
        new TransactionResult(t.id, shim.getStateGet,shim.getStateSet,shim.getStateDel,Option(r))
      }

    } catch {
      case e: Throwable =>
        RepLogger.except4Throwable(RepLogger.Sandbox_Logger, t.id, e)
        //akka send 无法序列化原始异常,简化异常信息
        val e1 = new SandboxException(e.getMessage)
        RepLogger.sendAlertToDB(pe.getRepChainContext.getHttpLogger(),new AlertInfo("CONTRACT",4,s"Node Name=${pe.getSysTag},txid=${t.id},erroInfo=${e.getMessage},Transaction Exception."))
        //shim.srOfTransaction.rollback
        pe.getRepChainContext.getBlockPreload(dotrans.da).getTransactionPreload(dotrans.t.id).rollback
        new TransactionResult(t.id, Map.empty,Map.empty,Map.empty,Option(ActionResult(102,e1.getMessage)))
    }
  }
}