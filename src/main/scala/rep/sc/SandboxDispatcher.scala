package rep.sc

import akka.actor.{ActorRef, Props, actorRef2Scala}
import rep.sc.Sandbox._

import scala.concurrent.duration._
import akka.util.Timeout
import rep.sc.scalax.SandboxScala
import rep.network.base.ModuleBase
import rep.log.RepLogger
import akka.routing._
import rep.api.rest.ResultCode
import rep.proto.rc2.{ActionResult, ChaincodeDeploy, ChaincodeId, Transaction, TransactionResult}
import rep.sc.isCLWasm.SandboxIsCLWasm
import rep.sc.wasmer.SandboxWasmer
import rep.storage.chain.KeyPrefixManager


/**
 * 伴生对象，预定义了交易处理的异常描述，传入消息的case类，以及静态方法
 *  @author c4w
 *
 */

object SandboxDispatcher {
  def props(name: String, cid: String): Props = Props(classOf[SandboxDispatcher], name, cid)

  //交易处理异常信息预定义
  val ERR_DEPLOY_CODE = "deploy交易代码内容不允许为空"
  val ERR_INVOKE_CHAINCODEID_EMPTY = "非deploy交易必须指定chaincodeId"
  val ERR_INVOKE_CHAINCODE_NOT_EXIST = "调用的chainCode不存在"
  val ERR_REPEATED_CID = "存在重复的合约Id"
  val ERR_CODER = "合约只能由部署者升级更新"
  val ERR_DISABLE_CID = "合约处于禁用状态"
  val ERR_NONDID_CONTRACT = "系统不支持非DID账户管理合约"
  val ERR_NO_CROSS_CONTRACT = "调用了不支持跨链的合约"

  //权限检查异常消息预定义
  val ERR_NO_PERMISSION_OF_DEPLOY = "没有合约部署的权限"
  val ERR_NO_PERMISSION_OF_SETSTATE = "没有改变合约状态的权限"
  val ERR_NO_PERMISSION_OF_INVOKE = "没有执行合约方法的权限"
  val ERR_NO_FOUND_Signer = "没有找到合约部署的签名者信息"
  val ERR_Signer_INVAILD = "合约部署的签名者信息无效"
  val ERR_NO_OPERATE = "操作不存在"
  val ERR_INVALID_OPERATE = "操作已经失效"
  val ERR_NO_SIGNER = "实体账户不存在"
  val ERR_INVALID_SIGNER = "实体账户已经失效"
  val ERR_NO_OP_IN_AUTHORIZE = "没有找到授权的操作"
  val ERR_NO_AUTHORIZE = "授权已经不存在"
  val ERR_INVALID_AUTHORIZE = "授权已经失效"

  val ERR_WORLDSTATE_CANNOT_CONTAIN_UNDERSCORES = "Key不能包含下划线"

  //下属actor的命名前缀
  val PRE_STATE = "_STATE"

  /**
   * 从api请求传入的 处理的预执行交易的输入消息
   *  @constructor 对交易简单封装
   */

  /**
   * 从共识层传入的执行交易请求
   *  @constructor 根据待执行交易、来源actor指向、数据访问标示建立实例
   * 	@param ts 待执行交易
   *  @param da 数据访问标识，实际上是LevelDB在某个时刻的快照
   *  @param typeOfSender 请求执行交易者的类型，类型定义请参看TypeOfSender
   */
  final case class DoTransaction(ts: Seq[Transaction], da: String, typeOfSender: TypeOfSender.Value)
  final case class DoTransactionOfCrossContract(ts: Seq[Transaction], da: String, typeOfSender: TypeOfSender.Value)
  final case class DoTransactionOfCache(cacheIdentifier:String, da: String, typeOfSender: TypeOfSender.Value)
  /**
   * 发送给Sandbox的执行交易请求
   *  @constructor 待执行交易、数据访问快照、合约状态
   * 	@param ts 待执行交易
   *  @param da 数据访问标识，实际上是LevelDB在某个时刻的快照
   *  @param contractStateType 当前合约的状态，类型定义请参看ContractStateType
   */
  final case class DoTransactionOfSandbox(ts: Seq[Transaction], da: String, contractStateType: ContractStateType.Value,isCrossContract:Boolean)
  final case class DoTransactionOfSandboxOfCache(cacheIdentifier:String, da: String, contractStateType: ContractStateType.Value)

  final case class DoTransactionOfSandboxInSingle(t: Transaction, da: String, contractStateType: ContractStateType.Value,isCrossContract:Boolean)

  /**
   * 本消息用于从存储恢复合约对应的sandbox
   *  @constructor 根据待执行交易、来源actor指向、数据访问标示建立实例
   * 	@param t 待执行交易
   *  @param da 数据访问标示
   */
  final case class DeployTransaction(t: Transaction, da: String)

}

/**
 * 负责调度合约容器的actor
 * @author c4w
 * @constructor 以actor名称、数据访问实例标示、父actor指向创建调度actor
 * @param moduleName 模块名称
 * @param cid 链码id

 */
class SandboxDispatcher(moduleName: String, cid: String) extends ModuleBase(moduleName) {
  import SandboxDispatcher._
  import scala.collection.immutable._

  var ContractState: ContractStateType.Value = ContractStateType.ContractInNone
  //var txidOfContractDeploy: String = ""
  private var RouterOfParallelSandboxs: Router = null
  private var SerialSandbox: ActorRef = null
  private var DeployTransactionCache : Transaction = null

  //设置同步处理请求的最大等待时间
  implicit val timeout = Timeout(1000.seconds)

  /**
   * 从LevelDB中获取Chaincode所存在的交易
   * @author jiangbuyun
   * @Result 返回cid所在的交易
   */
  private def getTransOfContractFromLevelDB(da:String,oid:String): Option[Transaction] = {
    val bp = pe.getRepChainContext.getBlockPreload(da)
    val chainCodeName : String = if(cid.lastIndexOf(SplitChainCodeId)>0){
      cid.substring(0,cid.lastIndexOf(SplitChainCodeId))
    } else cid
    val txId = bp.getObjectFromDB[String](KeyPrefixManager.getWorldStateKey(pe.getRepChainContext.getConfig,cid,chainCodeName,oid))
    txId match {
      case None => None
      case _ => bp.getTransactionByTxId(txId.get)
    }
  }

  /**
   * 从Snapshot中判断合约是否存在
   * @author jiangbuyun
   * @Result 返回true，表示合约已经存在对应的实例中，否则，没有
   */
  private def IsContractInSnapshot(da: String,oid:String): Boolean = {
    val preload = pe.getRepChainContext.getBlockPreload(da)
    val chainCodeName : String = if(cid.lastIndexOf(SplitChainCodeId)>0){
      cid.substring(0,cid.lastIndexOf(SplitChainCodeId))
    } else cid
    val txId = preload.get(KeyPrefixManager.getWorldStateKey(pe.getRepChainContext.getConfig,cid,chainCodeName,oid))
    txId match {
      case null => false
      case _ => true
    }
  }

  /**
   * 设置当前合约分派器中合约的状态，inleveldb、insnapshot、none
   * @author jiangbuyun
   */
  private def SetContractState(t: Transaction, da: String) = {
    if (this.ContractState == ContractStateType.ContractInSnapshot || this.ContractState == ContractStateType.ContractInNone) {
      val ctx = getTransOfContractFromLevelDB(da,t.oid)
      if (ctx == None) {
        //cc not exist
        if (t.`type` == Transaction.Type.CHAINCODE_DEPLOY) {
          if (IsContractInSnapshot(da,t.oid)) {
            throw new SandboxException(ERR_REPEATED_CID)
          }else{
            this.ContractState = ContractStateType.ContractInSnapshot
            this.DeployTransactionCache = t
          }
        } else {
          if (IsContractInSnapshot(da,t.oid)) {
            //contract in snapshot
            if (this.ContractState == ContractStateType.ContractInNone) {
              this.ContractState = ContractStateType.ContractInSnapshot
            }
          } else {
            //contract not exist,throw exception
            throw new SandboxException(ERR_INVOKE_CHAINCODE_NOT_EXIST)
          }
        }
      } else {
        //cc exist,contract in leveldb
        this.ContractState = ContractStateType.ContractInLevelDB
        this.DeployTransactionCache = ctx.get
      }
    }
  }

  /**
   * 建立并行处理交易的路由器，用于并发执行交易，采用路由器分发交易的方式达到交易并行执行的目标
   * @author jiangbuyun
   */
  private def createParallelRouter(chaincodeid: ChaincodeId, cType: ChaincodeDeploy.CodeType) = {
    if (RouterOfParallelSandboxs == null) {
      var list: Array[Routee] = new Array[Routee](pe.getRepChainContext.getConfig.getTransactionNumberOfProcessor)
      for (i <- 0 to pe.getRepChainContext.getConfig.getTransactionNumberOfProcessor - 1) {
        val ca = CreateSandbox(cType, chaincodeid, "sandbox_for_Parallel_of_router_" + cid + "_" + i)
        context.watch(ca)
        list(i) = new ActorRefRoutee(ca)
      }
      val rlist: IndexedSeq[Routee] = list.toIndexedSeq
      RouterOfParallelSandboxs = Router(SmallestMailboxRoutingLogic(), rlist)
    }
  }

  /**
   * 建立并行处理交易的路由器，用于并发执行交易，采用路由器分发交易的方式达到交易并行执行的目标
   * @author jiangbuyun
   */
  private def createSerialSandbox(chaincodeid: ChaincodeId, cType: ChaincodeDeploy.CodeType) = {
    if (this.SerialSandbox == null) {
      SerialSandbox = CreateSandbox(cType, chaincodeid, "sandbox_for_Serial_" + cid)
      context.watch(SerialSandbox)
    }
  }

  /**
   * 负责调度合约容器的actor
   * @author c4w
   * @modify by jiangbuyun
   * 用于建立sandbox
   * @param cType 合约容器的类型，目前只支持scala
   * @param cid 合约的ChaincodeId
   * @param sandboxName 该合约容器的名称
   */
  private def CreateSandbox(cType: ChaincodeDeploy.CodeType, cid: ChaincodeId, sandboxName: String): ActorRef = {
    cType match {
      case ChaincodeDeploy.CodeType.CODE_JAVASCRIPT =>
        null
      case ChaincodeDeploy.CodeType.CODE_SCALA =>
        context.actorOf(Props(new SandboxScala(cid)), sandboxName)
      case ChaincodeDeploy.CodeType.CODE_VCL_DLL =>
        null
      case ChaincodeDeploy.CodeType.CODE_VCL_EXE =>
        null
      case ChaincodeDeploy.CodeType.CODE_VCL_WASM =>
        context.actorOf(Props(new SandboxIsCLWasm(cid)), sandboxName)
      case ChaincodeDeploy.CodeType.CODE_WASM =>
        context.actorOf(Props(new SandboxWasmer(cid)), sandboxName)
      case _=>
        null
    }
  }

  private def Dispatch(ts:Seq[Transaction], da: String, typeOfSender: TypeOfSender.Value,cacheIdentifier:String,isCrossContract:Boolean) = {
    try {
      val special_t = ts(0)
      RepLogger.debug(RepLogger.Sandbox_Logger, s"entry sandbox dispatcher for ${cid} , contract state ${this.ContractState},txid=${special_t.id},da=${da}.")
      SetContractState(special_t, da)
      RepLogger.debug(RepLogger.Sandbox_Logger, s"sandbox dispatcher ${cid},execute setcontractstate after , contract state ${this.ContractState},txid=${special_t.id},da=${da}.")
      if (this.ContractState == ContractStateType.ContractInLevelDB) {
        if(this.DeployTransactionCache.getSpec.rType == ChaincodeDeploy.RunType.RUN_PARALLEL){
          this.createParallelRouter(this.DeployTransactionCache.cid.get,
            this.DeployTransactionCache.para.spec.get.cType)
          RepLogger.debug(RepLogger.Sandbox_Logger, s"sandbox dispatcher ${cid},create parallel router after ,txid=${special_t.id},da=${da}.")
        }

      }
      this.createSerialSandbox(this.DeployTransactionCache.cid.get, this.DeployTransactionCache.para.spec.get.cType)
      RepLogger.debug(RepLogger.Sandbox_Logger, s"sandbox dispatcher ${cid},create serial sandbox  after , contract state ${this.ContractState},txid=${special_t.id},da=${da}.")

      this.ContractState match {
        case ContractStateType.ContractInSnapshot =>
          RepLogger.debug(RepLogger.Sandbox_Logger, s"sandbox dispatcher ${cid},send msg to serial sandbox from preload or endorse,txid=${special_t.id},da=${da}.")
          if(cacheIdentifier == null)
            this.SerialSandbox.forward(DoTransactionOfSandbox(ts, da, this.ContractState,isCrossContract))
          else
            this.SerialSandbox.forward(DoTransactionOfSandboxOfCache(cacheIdentifier,da, this.ContractState))
        case ContractStateType.ContractInLevelDB =>
          typeOfSender match {
            case TypeOfSender.FromAPI =>
              RepLogger.debug(RepLogger.Sandbox_Logger, s"sandbox dispatcher ${cid},send msg to Parallel sandbox from api,txid=${special_t.id},da=${da}.")
              if(this.RouterOfParallelSandboxs == null){
                this.createParallelRouter(this.DeployTransactionCache.cid.get,
                                      this.DeployTransactionCache.para.spec.get.cType)
              }
              if(cacheIdentifier == null)
                this.RouterOfParallelSandboxs.route(DoTransactionOfSandbox(ts, da, this.ContractState,isCrossContract), sender)
              else
                this.RouterOfParallelSandboxs.route(DoTransactionOfSandboxOfCache(cacheIdentifier, da, this.ContractState), sender)
            case _ =>
              this.DeployTransactionCache.para.spec.get.rType match {
                case ChaincodeDeploy.RunType.RUN_PARALLEL =>
                  RepLogger.debug(RepLogger.Sandbox_Logger, s"sandbox dispatcher ${cid},send msg to Parallel sandbox from parallel,txid=${special_t.id},da=${da}.")
                  if(this.RouterOfParallelSandboxs == null){
                    this.createParallelRouter(this.DeployTransactionCache.cid.get,
                      this.DeployTransactionCache.para.spec.get.cType)
                  }
                  if(cacheIdentifier == null)
                    this.RouterOfParallelSandboxs.route(DoTransactionOfSandbox(ts, da, this.ContractState,isCrossContract), sender)
                  else
                    this.RouterOfParallelSandboxs.route(DoTransactionOfSandboxOfCache(cacheIdentifier, da, this.ContractState), sender)
                case _ => 
                  RepLogger.debug(RepLogger.Sandbox_Logger, s"sandbox dispatcher ${cid},send msg to serial sandbox from other reason,txid=${special_t.id},da=${da}.")
                  if(cacheIdentifier == null)
                    this.SerialSandbox.forward(DoTransactionOfSandbox(ts, da, this.ContractState,isCrossContract))
                  else
                    this.SerialSandbox.forward(DoTransactionOfSandboxOfCache(cacheIdentifier,da, this.ContractState))
              }
          }
      }
    } catch {
      case e: Exception =>
        RepLogger.except4Throwable(RepLogger.Sandbox_Logger,e.getMessage,e)
        val rs = createErrorData(ts.toSeq,Option(akka.actor.Status.Failure(e)))
        //向请求发送方返回包含执行异常的结果
        sender ! rs.toSeq
    }
  }

  private def createErrorData(ts: scala.collection.Seq[Transaction], err: Option[akka.actor.Status.Failure]): Array[TransactionResult] = {
    var rs = scala.collection.mutable.ArrayBuffer[TransactionResult]()
    ts.foreach(t => {
      rs += new TransactionResult(t.id, Map.empty,Map.empty,Map.empty, Option(ActionResult(ResultCode.Sandbox_Exception_In_Dispatch, err.get.cause.getMessage)))
    })
    rs.toArray
  }
  /**
   * 请求消息的调度处理
   *
   */
  def receive = {
    //执行交易请求
    case ti: DoTransaction =>
      Dispatch(ti.ts.asInstanceOf[Seq[Transaction]] ,ti.da,ti.typeOfSender,null,false)
    case ti:DoTransactionOfCrossContract =>
      Dispatch(ti.ts.asInstanceOf[Seq[Transaction]] ,ti.da,ti.typeOfSender,null,true)
    case tiOfCache:DoTransactionOfCache =>
      val ts = pe.getTrans(tiOfCache.cacheIdentifier)
      if(ts != null){
        Dispatch(ts.asInstanceOf[Seq[Transaction]],tiOfCache.da,tiOfCache.typeOfSender,tiOfCache.cacheIdentifier,false)
      }
  }

}