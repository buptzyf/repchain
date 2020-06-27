package rep.sc

import akka.actor.{ Actor, ActorRef, Props, actorRef2Scala }
import delight.nashornsandbox._
import rep.protos.peer._
import rep.sc.Sandbox._
import rep.utils.{ GlobalUtils, TimeUtils }

import rep.storage._

import scala.concurrent.duration._
import scala.concurrent._
import akka.util.Timeout
import akka.pattern.ask
import java.lang.Exception

import rep.storage.IdxPrefix.WorldStateKeyPreFix
import rep.utils.SerializeUtils
import rep.network.tools.PeerExtension
import rep.sc.scalax.SandboxScala
import rep.utils.SerializeUtils.deserialise
import rep.utils.SerializeUtils.serialise
import org.slf4j.LoggerFactory
import rep.network.base.ModuleBase

import rep.log.RepLogger
import akka.routing._;
import rep.app.conf.SystemProfile

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

  //下属actor的命名前缀
  val PRE_SUB_ACTOR = "sb_"
  val PRE_STATE = "_STATE"

  /**
   * 从共识层传入的执行交易请求
   *  @constructor 根据待执行交易、来源actor指向、数据访问标示建立实例
   * 	@param t 待执行交易
   *  @param da 数据访问标识，实际上是LevelDB在某个时刻的快照
   *  @param typeOfSender 请求执行交易者的类型，类型定义请参看TypeOfSender
   */
  final case class DoTransaction(t: Transaction, da: String, typeOfSender: TypeOfSender.Value)

  /**
   * 发送给Sandbox的执行交易请求
   *  @constructor 待执行交易、数据访问快照、合约状态
   * 	@param t 待执行交易
   *  @param da 数据访问标识，实际上是LevelDB在某个时刻的快照
   *  @param contractStateType 当前合约的状态，类型定义请参看ContractStateType
   */
  final case class DoTransactionOfSandbox(t: Transaction, da: String, contractStateType: ContractStateType.Value)

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
  private def getTransOfContractFromLevelDB: Option[Transaction] = {
    val db = ImpDataAccess.GetDataAccess(pe.getSysTag)
    val key_tx = WorldStateKeyPreFix + cid
    val txid = db.Get(key_tx)
    if (txid == null) {
      None
    } else {
      //this.txidOfContractDeploy = deserialise(txid).asInstanceOf[String]
      //db.getTransDataByTxId(this.txidOfContractDeploy)
      db.getTransDataByTxId(deserialise(txid).asInstanceOf[String])
    }
  }

  /**
   * 从Snapshot中判断合约是否存在
   * @author jiangbuyun
   * @Result 返回true，表示合约已经存在对应的实例中，否则，没有
   */
  private def IsContractInSnapshot(da: String): Boolean = {
    var b = false
    val key_tx = WorldStateKeyPreFix + cid
    val snapshot = ImpDataPreloadMgr.GetImpDataPreload(pe.getSysTag, da)
    val txid = snapshot.Get(key_tx)
    if (txid != null) {
      //val txid_str = deserialise(txid).asInstanceOf[String]
      //if (txid_str == this.txidOfContractDeploy) {
        b = true
      //}
    }
    b
  }

  /**
   * 设置当前合约分派器中合约的状态，inleveldb、insnapshot、none
   * @author jiangbuyun
   */
  private def SetContractState(t: Transaction, da: String) = {
    if (this.ContractState == ContractStateType.ContractInSnapshot || this.ContractState == ContractStateType.ContractInNone) {
      val ctx = getTransOfContractFromLevelDB
      if (ctx == None) {
        //cc not exist
        if (t.`type` == Transaction.Type.CHAINCODE_DEPLOY) {
          if (IsContractInSnapshot(da)) {
            throw new SandboxException(ERR_REPEATED_CID)
          }else{
            this.ContractState = ContractStateType.ContractInSnapshot
            this.DeployTransactionCache = t
          }
        } else {
          if (IsContractInSnapshot(da)) {
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
  private def createParallelRouter(chaincodeid: ChaincodeId, ctype: ChaincodeDeploy.CodeType) = {
    if (RouterOfParallelSandboxs == null) {
      var list: Array[Routee] = new Array[Routee](SystemProfile.getNumberOfTransProcessor)
      for (i <- 0 to SystemProfile.getNumberOfTransProcessor - 1) {
        var ca = CreateSandbox(ctype, chaincodeid, "sandbox_for_Parallel_of_router_" + cid + "_" + i)
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
  private def createSerialSandbox(chaincodeid: ChaincodeId, ctype: ChaincodeDeploy.CodeType) = {
    if (this.SerialSandbox == null) {
      SerialSandbox = CreateSandbox(ctype, chaincodeid, "sandbox_for_Serial_" + cid)
      context.watch(SerialSandbox)
    }
  }

  /**
   * 负责调度合约容器的actor
   * @author c4w
   * @modify by jiangbuyun
   * 用于建立sandbox
   * @param ctype 合约容器的类型，目前只支持scala
   * @param cid 合约的ChaincodeId
   * @param sandboxName 该合约容器的名称
   */
  private def CreateSandbox(ctype: ChaincodeDeploy.CodeType, cid: ChaincodeId, sandboxName: String): ActorRef = {
    ctype match {
      case ChaincodeDeploy.CodeType.CODE_SCALA | ChaincodeDeploy.CodeType.CODE_SCALA_PARALLEL =>
        context.actorOf(Props(new SandboxScala(cid)), sandboxName)
      //默认采用Scala容器
      case _ => context.actorOf(Props(new SandboxScala(cid)), sandboxName)
    }
  }

  private def Dispatch(dotrans: DoTransaction) = {
    try {
      RepLogger.debug(RepLogger.Sandbox_Logger, s"entry sandbox dispatcher for ${cid} , contract state ${this.ContractState},txid=${dotrans.t.id},da=${dotrans.da}.")
      SetContractState(dotrans.t, dotrans.da)
      RepLogger.debug(RepLogger.Sandbox_Logger, s"sandbox dispatcher ${cid},execute setcontractstate after , contract state ${this.ContractState},txid=${dotrans.t.id},da=${dotrans.da}.")
      if (this.ContractState == ContractStateType.ContractInLevelDB) {
        this.createParallelRouter(this.DeployTransactionCache.cid.get, this.DeployTransactionCache.para.spec.get.ctype)
        RepLogger.debug(RepLogger.Sandbox_Logger, s"sandbox dispatcher ${cid},create parallel router after ,txid=${dotrans.t.id},da=${dotrans.da}.")
      }
      this.createSerialSandbox(this.DeployTransactionCache.cid.get, this.DeployTransactionCache.para.spec.get.ctype)
      RepLogger.debug(RepLogger.Sandbox_Logger, s"sandbox dispatcher ${cid},create serial sandbox  after , contract state ${this.ContractState},txid=${dotrans.t.id},da=${dotrans.da}.")

      this.ContractState match {
        case ContractStateType.ContractInSnapshot =>
          RepLogger.debug(RepLogger.Sandbox_Logger, s"sandbox dispatcher ${cid},send msg to serial sandbox from preload or endorse,txid=${dotrans.t.id},da=${dotrans.da}.")
          this.SerialSandbox.forward(DoTransactionOfSandbox(dotrans.t, dotrans.da, this.ContractState))
        case ContractStateType.ContractInLevelDB =>
          dotrans.typeOfSender match {
            case TypeOfSender.FromAPI =>
              RepLogger.debug(RepLogger.Sandbox_Logger, s"sandbox dispatcher ${cid},send msg to Parallel sandbox from api,txid=${dotrans.t.id},da=${dotrans.da}.")
              this.RouterOfParallelSandboxs.route(DoTransactionOfSandbox(dotrans.t, dotrans.da, this.ContractState), sender)
            case _ =>
              this.DeployTransactionCache.para.spec.get.ctype match {
                case ChaincodeDeploy.CodeType.CODE_SCALA_PARALLEL =>
                  RepLogger.debug(RepLogger.Sandbox_Logger, s"sandbox dispatcher ${cid},send msg to Parallel sandbox from parallel,txid=${dotrans.t.id},da=${dotrans.da}.")
                  this.RouterOfParallelSandboxs.route(DoTransactionOfSandbox(dotrans.t, dotrans.da, this.ContractState), sender)
                case _ => 
                  RepLogger.debug(RepLogger.Sandbox_Logger, s"sandbox dispatcher ${cid},send msg to serial sandbox from other reason,txid=${dotrans.t.id},da=${dotrans.da}.")
                  this.SerialSandbox.forward(DoTransactionOfSandbox(dotrans.t, dotrans.da, this.ContractState))
              }
          }
      }
    } catch {
      case e: Exception =>
        val r = new DoTransactionResult(dotrans.t.id, null, null,
          Option(akka.actor.Status.Failure(e)))
        //向请求发送方返回包含执行异常的结果
        sender ! r
    }
  }

  /**
   * 请求消息的调度处理
   *
   */
  def receive = {
    //执行交易请求
    case ti: DoTransaction =>
      Dispatch(ti)
  }

}