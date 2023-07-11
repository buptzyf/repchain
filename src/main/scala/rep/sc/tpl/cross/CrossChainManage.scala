package rep.sc.tpl.cross

import CrossManage.{chainInfoPrefix, contractInfoPrefix, crossTranPrefix, crossTranReceiptPrefix, routeInfoPrefix, userPermitRoutePrefix}
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse
import org.json4s.jackson.Serialization.{read, write}
import org.slf4j.Logger
import rep.proto.rc2.ActionResult
import rep.sc.scalax.{ContractContext, ContractException, IContract}


/**
 *
 * @param chainHash            链或适配器ID
 * @param chainType            链类型 fabric/repchain/fisco-bcos
 * @param consensusNodeUrlList 节点的ip:port
 * @param adapterName          适配器名
 */
case class ChainInfo(chainHash: String, chainType: Int, configPath: String, consensusNodeUrlList: Option[String], adapterName: String)

/**
 *
 * @param contractHash 合约ID
 * @param chainHash    链或适配器ID
 * @param contractName 合约描述
 * @param contractAddr 合约地址
 */
case class ContractInfo(contractHash: String, chainHash: String, contractName: String, contractAddr: String)

/**
 *
 * @param routeHash        路由ID
 * @param routeName        路由描述
 * @param contractHashFrom 原合约ID
 * @param contractHashTo   目标合约ID
 */
case class RouteInfo(routeHash: String, routeName: String, contractHashFrom: String, contractHashTo: String)

/**
 *
 * @param creditCode 用户ID
 * @param routeSeq   路由序列
 */
case class PermitRoute(creditCode: String, routeSeq: Seq[String])

/**
 *
 * @param chainHash  链ID
 * @param routeHash  路由ID
 * @param accountId  账户ID
 * @param methodName 方法名
 * @param methodArgs 方法参数
 */
case class CrossTransaction(chainHash: String, routeHash: String, accountId: String, methodName: String, methodArgs: String)

/**
 *
 * @param chainHash        链ID
 * @param crossTranHashSrc 代理交易的hash
 * @param crossTranHash    目标链交易Hash
 */
case class CrossTransactionReceipt(chainHash: String, crossTranHashSrc: String, crossTranHash: String)

/**
 * @author zyf
 */
class CrossChainManage extends IContract {

  implicit val formats: DefaultFormats.type = DefaultFormats
  var logger: Logger = _

  def init(ctx: ContractContext): Unit = {
    this.logger = ctx.api.getLogger
    logger.info(s"init | 初始化跨链管理合约：${ctx.t.cid.get.chaincodeName}, 交易ID为：${ctx.t.id}")
  }

  /**
   * 登记chainInfo
   *
   * @param ctx
   * @param sdata
   * @return
   */
  def registerChainInfo(ctx: ContractContext, sdata: String): ActionResult = {
    val chainInfo = parse(sdata).extract[ChainInfo]
    logger.info("registerChainInfo | chainInfo: {}", sdata)
    val chainInfoKey = chainInfoPrefix + chainInfo.chainHash
    if (ctx.api.getVal(chainInfoKey) != null) {
      logger.error(s"registerChainInfo | 已经有该id ${chainInfo.chainHash} 了，请使用updateChainInfo")
      throw ContractException(s"registerChainInfo | 已经有该id ${chainInfo.chainHash} 了，请使用updateChainInfo")
    }
    ctx.api.setVal(chainInfoKey, sdata)
    ActionResult()
  }

  /**
   * 更新chainInfo
   *
   * @param ctx
   * @param sdata
   * @return
   */
  def updateChainInfo(ctx: ContractContext, sdata: String): ActionResult = {
    val chainInfo = parse(sdata).extract[ChainInfo]
    logger.info("updateChainInfo | chainInfo: {}", sdata)
    val chainInfoKey = chainInfoPrefix + chainInfo.chainHash
    if (ctx.api.getVal(chainInfoKey) == null) {
      logger.error(s"updateChainInfo | 不存在该id ${chainInfo.chainHash} 了，请使用registerChainInfo")
      throw ContractException(s"updateChainInfo | 不存在该id ${chainInfo.chainHash} 了，请使用registerChainInfo")
    }
    ctx.api.setVal(chainInfoKey, sdata)
    ActionResult()
  }

  /**
   *
   * @param ctx
   * @param sdata
   * @return
   */
  def registerContractInfo(ctx: ContractContext, sdata: String): ActionResult = {
    val contractInfo = parse(sdata).extract[ContractInfo]
    logger.info("registerContractInfo | contractInfo: {}", sdata)
    val contractInfoKey = contractInfoPrefix + contractInfo.contractHash
    if (ctx.api.getVal(contractInfoKey) != null) {
      logger.error(s"registerContractInfo | 已经有该id ${contractInfo.contractHash} 了，暂不支持重复登记或更新")
      throw ContractException(s"registerContractInfo | 已经有该id ${contractInfo.contractHash} 了，暂不支持重复登记或更新")
    }
    ctx.api.setVal(contractInfoKey, sdata)
    ActionResult()
  }

  /**
   *
   * @param ctx
   * @param sdata
   * @return
   */
  def registerRouterInfo(ctx: ContractContext, sdata: String): ActionResult = {
    val routeInfo = parse(sdata).extract[RouteInfo]
    logger.info("registerRouterInfo | routeInfo: {}", sdata)
    val routeInfoKey = routeInfoPrefix + routeInfo.routeHash
    if (ctx.api.getVal(routeInfoKey) != null) {
      logger.error(s"registerRouterInfo | 已经有该id ${routeInfo.routeHash} 了，暂不支持重复登记或更新")
      throw ContractException(s"registerRouterInfo | 已经有该id ${routeInfo.routeHash} 了，暂不支持重复登记或更新")
    }
    ctx.api.setVal(routeInfoKey, sdata)
    ActionResult()
  }

  /**
   *
   * @param ctx
   * @param sdata
   * @return
   */
  def registerPermitRoute(ctx: ContractContext, sdata: String): ActionResult = {
    val permitInfo = parse(sdata).extract[PermitRoute]
    logger.info("registerPermitRoute | permitInfo: {}", sdata)
    val permitInfoKey = userPermitRoutePrefix + permitInfo.creditCode
    ctx.api.setVal(permitInfoKey, sdata)
    ActionResult()
  }

  /**
   *
   * @param ctx
   * @param sdata
   * @return
   */
  def updatePermitRoute(ctx: ContractContext, sdata: String): ActionResult = {
    val permitInfo = parse(sdata).extract[PermitRoute]
    logger.info("updatePermitChain | permitInfo: {}", sdata)
    val permitInfoKey = userPermitRoutePrefix + permitInfo.creditCode
    ctx.api.setVal(permitInfoKey, sdata)
    ActionResult()
  }

  /**
   *
   * @param ctx
   * @param sdata
   * @return
   */
  def submitCrossProxyTran(ctx: ContractContext, sdata: String): ActionResult = {
    val permitInfoKey = userPermitRoutePrefix + ctx.t.getSignature.getCertId.creditCode
    val permitInfo = parse(ctx.api.getVal(permitInfoKey).asInstanceOf[String]).extract[PermitRoute]
    val crossTran = parse(sdata).extract[CrossTransaction]
    logger.info(s"submitCrossProxyTran | crossTran: $sdata, permitInfo: ${write(permitInfo)}")
    if (permitInfo.routeSeq.contains(crossTran.routeHash)) {
      val routeInfo = parse(ctx.api.getVal(routeInfoPrefix + crossTran.routeHash).asInstanceOf[String]).extract[RouteInfo]
      val contractHashTo = routeInfo.contractHashTo
      logger.info(s"submitCrossProxyTran | 有向路由 ${write(routeInfo)} 合约 $contractHashTo 提交跨链交易的权限")
      ctx.api.setVal(crossTranPrefix + ctx.t.id, sdata)
    } else {
      logger.error(s"submitCrossProxyTran | 不具有向链 ${crossTran.chainHash} 提交跨链交易的权限")
      throw ContractException(s"submitCrossProxyTran | 不具有向链 ${crossTran.chainHash} 提交跨链交易的权限")
    }
    ActionResult()
  }

  /**
   *
   * @param ctx
   * @param sdata
   * @return
   */
  def submitCrossProxyReceipt(ctx: ContractContext, sdata: String): ActionResult = {
    val crossTranReceipt = parse(sdata).extract[CrossTransactionReceipt]
    logger.info("submitCrossProxyReceipt | receipt: {}", sdata)
    ctx.api.setVal(crossTranReceiptPrefix + crossTranReceipt.crossTranHash, write(parse(sdata) merge parse(s"""{"receiptTranHash":"${ctx.t.id}"}""")))
    ActionResult()
  }

  def onAction(ctx: ContractContext, action: String, sdata: String): ActionResult = {
    action match {
      case "registerChainInfo" => registerChainInfo(ctx, sdata)
      case "updateChainInfo" => updateChainInfo(ctx, sdata)
      case "registerContractInfo" => registerContractInfo(ctx, sdata)
      case "registerRouterInfo" => registerRouterInfo(ctx, sdata)
      case "registerPermitRoute" => registerPermitRoute(ctx, sdata)
      case "updatePermitRoute" => updatePermitRoute(ctx, sdata)
      case "submitCrossProxyTran" => submitCrossProxyTran(ctx, sdata)
      case "submitCrossProxyReceipt" => submitCrossProxyReceipt(ctx, sdata)
      case _ => throw ContractException("该合约没有改方法")
    }
  }
}