package rep.sc.wasmer

import rep.proto.rc2.{ActionResult, ChaincodeId, Transaction, TransactionResult}
import rep.sc.{Sandbox, SandboxDispatcher}
import org.wasmer.Module
import rep.api.rest.ResultCode
import rep.log.RepLogger
import rep.log.httplog.AlertInfo
import rep.sc.Sandbox.{ERR_UNKNOWN_TRANSACTION_TYPE, SandboxException}
import rep.sc.SandboxDispatcher.ERR_INVOKE_CHAINCODE_NOT_EXIST
import rep.sc.scalax.ContractContext
import rep.utils.IdTool

class SandboxWasmer(cid: ChaincodeId)  extends Sandbox(cid){
  var cobj: Module = null

  private def LoadClass(ctx: ContractContext, txcid: String, t: Transaction,isInit:Boolean=false) = {
    val fn = ctx.api.getChainNetId+IdTool.WorldStateKeySeparator+txcid+".wasm"
    if(CompileOfWasmer.isCompiled(fn)){
      cobj = CompileOfWasmer.CompileFromFile(fn)
    }else{
      val code = t.para.spec.get.codePackage
      cobj = CompileOfWasmer.CompileAndSave(code, fn)
    }
    if(isInit){
      invokerOfWasmer.invokeOfInit(cobj,ctx)
    }
  }



  /**
   * 交易处理抽象方法，接受待处理交易，返回处理结果
   *
   * @return 交易执行结果
   */
  override def doTransaction(dotrans: SandboxDispatcher.DoTransactionOfSandboxInSingle): TransactionResult = {
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
          LoadClass(ctx, tx_cid, t,true)
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
            if (deployTransaction == None)
              throw new SandboxException(ERR_INVOKE_CHAINCODE_NOT_EXIST)
            this.LoadClass(ctx, tx_cid, deployTransaction.get,false)
          }
          invokerOfWasmer.onAction(cobj,ctx)
        case Transaction.Type.CHAINCODE_SET_STATE =>
          val key_tx_state = tx_cid + PRE_STATE //KeyPrefixManager.getWorldStateKey(pe.getRepChainContext.getConfig,tx_cid+PRE_STATE,t.getCid.chaincodeName,t.oid)
          shim.setVal(key_tx_state, t.para.state.get)
          this.ContractStatus = Some(t.para.state.get)
          this.ContractStatusSource = Some(2)
          null
        case _ => throw SandboxException(ERR_UNKNOWN_TRANSACTION_TYPE)
      }
      pe.getRepChainContext.getBlockPreload(dotrans.da).getTransactionPreload(dotrans.t.id).commit
      //shim.srOfTransaction.commit
      if (r == null) {
        new TransactionResult(t.id, shim.getStateGet, shim.getStateSet, shim.getStateDel, Option(new ActionResult(ResultCode.Sandbox_Success, "")))
      } else {
        new TransactionResult(t.id, shim.getStateGet, shim.getStateSet, shim.getStateDel, Option(r))
      }

    } catch {
      case e: Throwable =>
        RepLogger.except4Throwable(RepLogger.Sandbox_Logger, t.id, e)
        //akka send 无法序列化原始异常,简化异常信息
        val e1 = new SandboxException(e.getMessage)
        RepLogger.sendAlertToDB(pe.getRepChainContext.getHttpLogger(), new AlertInfo("CONTRACT", 4, s"Node Name=${pe.getSysTag},txid=${t.id},erroInfo=${e.getMessage},Transaction Exception."))
        //shim.srOfTransaction.rollback
        pe.getRepChainContext.getBlockPreload(dotrans.da).getTransactionPreload(dotrans.t.id).rollback
        new TransactionResult(t.id, Map.empty, Map.empty, Map.empty, Option(ActionResult(ResultCode.Transaction_Exception_In_SandboxOfScala, e1.getMessage)))
    }
  }
}
