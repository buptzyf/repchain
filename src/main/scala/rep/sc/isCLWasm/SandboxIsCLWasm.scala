package rep.sc.isCLWasm

import org.json4s.{DefaultFormats, JObject }
import org.json4s.jackson.JsonMethods.parse
import org.wasmer.Module
import rep.api.rest.ResultCode
import rep.log.RepLogger
import rep.log.httplog.AlertInfo
import rep.proto.rc2.{ActionResult, ChaincodeId, Transaction, TransactionResult}
import rep.sc.Sandbox.{ERR_UNKNOWN_TRANSACTION_TYPE, SandboxException}
import rep.sc.SandboxDispatcher.ERR_INVOKE_CHAINCODE_NOT_EXIST
import rep.sc.scalax.ContractContext
import rep.sc.{Sandbox, SandboxDispatcher}
import rep.utils.IdTool
import rep.sc.isCLWasm.Utils

class SandboxIsCLWasm(cid: ChaincodeId, invokerOfIsCLWasm: InvokerOfIsCLWasm = new InvokerOfIsCLWasm(new Utils), utils: Utils = new Utils) extends Sandbox(cid) {
  protected val PRE_ABI = "_ABI"
  implicit val formats = DefaultFormats
  var cobj: Module = null
  var abi: JObject = null

  // TODO: 分离编译和执行init方法的逻辑
  private def LoadClass(ctx: ContractContext, txcid: String, t: Transaction) = {
    val fn = ctx.api.getChainNetId + IdTool.WorldStateKeySeparator + txcid + ".wasm"
    if (CompilerOfIsCLWasm.isCompiled(fn)) {
      cobj = CompilerOfIsCLWasm.CompileFromFile(fn)
    } else {
      val code = t.para.spec.get.codePackage
      cobj = CompilerOfIsCLWasm.CompileAndSave(code, fn)
    }

    if (t.`type` == Transaction.Type.CHAINCODE_DEPLOY) {
      val json = parse(ctx.t.para.spec.get.initParameter)
      invokerOfIsCLWasm.invokeOfInit(cobj, abi, ctx, json.extract[java.util.ArrayList[String]])
    }
  }

  /**
   * 交易处理抽象方法，接受待处理交易，返回处理结果
   *
   * @return 交易执行结果
   */
  override def doTransaction(dotrans: SandboxDispatcher.DoTransactionOfSandboxInSingle): TransactionResult = {
    val t = dotrans.t
    val ctx = new ContractContext(shim, t)
    //如果执行中出现异常,返回异常
    try {
      val tx_cid = IdTool.getTXCId(t)
      val r: ActionResult = t.`type` match {
        case Transaction.Type.CHAINCODE_DEPLOY =>
          val sc = utils.parseSmartContract(t.para.spec.get.codePackage)
          //热加载code对应的class
          LoadClass(ctx, tx_cid, t)
          DoDeploy(tx_cid, t, sc.abi)
          this.abi = parse(sc.abi).asInstanceOf[JObject]
          null
        //由于Invoke为最频繁调用，因此应尽量避免在处理中I/O读写,比如合约状态的检查就最好在内存中处理
        //TODO case  Transaction.Type.CHAINCODE_DESC 增加对合约描述的处理
        case Transaction.Type.CHAINCODE_INVOKE =>
          if (this.cobj == null) {
            val deployTxid = shim.getVal(tx_cid)
            if (deployTxid == null)
              throw SandboxException(ERR_INVOKE_CHAINCODE_NOT_EXIST)
            val deployTransaction = pe.getRepChainContext.getBlockSearch.getTransactionByTxId(deployTxid.toString)
            if (deployTransaction == None)
              throw SandboxException(ERR_INVOKE_CHAINCODE_NOT_EXIST)
            val abiJson = shim.getVal(PRE_ABI + tx_cid);
            if (abiJson == null)
              throw SandboxException(ERR_INVOKE_CHAINCODE_NOT_EXIST)
            this.abi = parse(abiJson.asInstanceOf[String]).asInstanceOf[JObject]
            this.LoadClass(ctx, tx_cid, deployTransaction.get)
          }
          this.ExecutionInTimeoutManagement(timeout)(invokerOfIsCLWasm.onAction(cobj, abi, ctx))
        case Transaction.Type.CHAINCODE_SET_STATE =>
          val key_tx_state = tx_cid + PRE_STATE
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
        if(r.code == 0){
          new TransactionResult(t.id, shim.getStateGet, shim.getStateSet, shim.getStateDel, Option(r))
        }else{
          pe.getRepChainContext.getBlockPreload(dotrans.da).getTransactionPreload(dotrans.t.id).rollback
          RepLogger.sendAlertToDB(pe.getRepChainContext.getHttpLogger(),
            new AlertInfo("CONTRACT", 4, s"Node Name=${pe.getSysTag},txid=${t.id}," +
              s"erroInfo=${r.reason},Transaction Exception."))
          new TransactionResult(t.id, Map.empty, Map.empty, Map.empty, Option(ActionResult(ResultCode.Transaction_Exception_In_SandboxOfScala, r.reason)))
        }
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

  /**
   * 重载部署合约时记录数据的逻辑
   * @param tx_cid 字符串表示的合约id
   * @param t 部署合约交易
   */
  def DoDeploy(tx_cid: String, t: Transaction, abi: String) = {
    // 记录合约的部署交易id
    shim.setVal(tx_cid, t.id)

    // 记录合约的开发者id(以合约名为key，相当于只记录了某合约的最新版本的部署者，是否应当记录每个版本的部署者信息？)
    val cn = cid.chaincodeName
    val coder = t.signature.get.certId.get.creditCode
    shim.setVal(cn, coder)

    // 记录合约初始状态
    shim.setVal(tx_cid + PRE_STATE, true)
    this.ContractStatus = Some(true)
    this.ContractStatusSource = Some(2)

    // 记录合约abi信息，包括：world states变量数据结构信息，以及合约方法signature
    // 便于在后续调用合约时利用该信息反序列化合约方法参数及序列化/反序列化world states
    shim.setVal(tx_cid + PRE_ABI, abi)
  }
}
