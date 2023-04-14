package rep.censor

import akka.actor.Props
import com.baidu.aip.contentcensor.AipContentCensor
import org.apache.commons.lang3.StringUtils
import org.json.JSONObject
import rep.app.conf.RepChainConfig
import rep.log.RepLogger
import rep.network.autotransaction.Topic
import rep.network.base.ModuleBase
import rep.network.consensus.byzantium.ConsensusCondition
import rep.proto.rc2.{Block, ChaincodeId}
import rep.proto.rc2.Transaction.Type
import rep.storage.chain.block.BlockSearcher
import rep.utils.{IdTool, SerializeUtils}

import java.util.concurrent.atomic.LongAdder
import scala.collection.mutable
import scala.concurrent.duration.DurationInt
import scala.util.control.Breaks._

object AICensor {
  def props(name: String): Props = Props(classOf[AICensor], name)

  case object AICensor
}

/**
 * 监管节点服务
 *
 * @param name 模块名称
 */
class AICensor(name: String) extends ModuleBase(name: String) {

  import context.dispatcher

  private val ctx = pe.getRepChainContext
  private val consensusCondition = new ConsensusCondition(ctx)
  private val config: RepChainConfig = pe.getRepChainContext.getConfig
  private val blockSearcher: BlockSearcher = pe.getRepChainContext.getBlockSearch
  private val pv = ctx.getPermissionVerify
  private var hashPermission = false
  private var client: Option[AipContentCensor] = None
  private val censoredHeight: LongAdder = new LongAdder()
  private val chaincodeId = new ChaincodeId("RegulateTPL", 1)
  private val sysTag = pe.getSysTag
  private val interval = config.censorInterval

  initClient()
  initCensoredHeight()

  case class Conclusion(height: Long, blockHash: String, illegalTrans: mutable.Map[String, String])

  /**
   * 初始化AipContentCensor客户端
   */
  def initClient(): Unit = {
    if (client.isEmpty) {
      if (StringUtils.isBlank(config.getAppId) || StringUtils.isBlank(config.getApiKey) || StringUtils.isBlank(config.getSecretKey)) {
        RepLogger.System_Logger.error("Init AipContentCensor error!")
        throw new Exception("Init AipContentCensor error!")
      }
      client = Some(new AipContentCensor(config.getAppId, config.getApiKey, config.getSecretKey))
      //client.setSocketTimeoutInMillis(3000)
      //client.setConnectionTimeoutInMillis(2000)
    }
  }

  /**
   * 初始化审查起始区块高度，不审查创世块
   */
  private def initCensoredHeight(): Unit = {
    val startHeight = if (config.getCensorHeight < 1) 1 else config.getCensorHeight
    censoredHeight.add(startHeight)
  }

  def censorBlock(srcBlock: Block): Int = {
    RepLogger.System_Logger.info(s"审查区块高度:${srcBlock.getHeader.height}")
    val map = mutable.Map[String, String]()
    srcBlock.transactions.foreach(t => {
      if (t.`type` == Type.CHAINCODE_INVOKE && (!t.getCid.chaincodeName.equals("RegulateTPL")
        && !t.getCid.chaincodeName.equals(config.getAccountContractName)
        && !t.getCid.chaincodeName.equals(config.getMemberManagementContractName))) {
        val result = autoCensor(t.getIpt.args.mkString(";"))
        if (result._1 == -1 || result._1 == -2 || result._1 == 4 || result._1 == 5) { // 审核失败，或有未知的异常，直接返回
          RepLogger.System_Logger.info(s"Censor failed. code=${result._1}, mgs=${result._2}")
          return -1
        }
        if (result._1 == 1) {
          RepLogger.System_Logger.info(s"censor result code=${result._1}, mgs=${result._2}, txid=${t.id}")
        } else if (result._1 == 2) {
          RepLogger.System_Logger.info(s"illegal transaction, blockHeight=${srcBlock.getHeader.height}, txid=${t.id}, reason=${result._2}")
          map += (t.id -> result._2)
        } else {
          RepLogger.System_Logger.info(s"censor result code=${result._1}, mgs=${result._2}")
        }
      } else {
        RepLogger.System_Logger.info(s"非invoke类型交易、监管交易、DID身份与权限管理交易、节点管理交易不做审查，txid=${t.id}")
      }
    })
    if (map.nonEmpty) { // map不为空，说明有违规
      val c = Conclusion(srcBlock.getHeader.height, srcBlock.getHeader.hashPresent.toStringUtf8, map)
      val transaction = ctx.getTransactionBuilder.createTransaction4Invoke(config.getChainNetworkId + IdTool.DIDPrefixSeparator + sysTag, chaincodeId,
        "regBlocks", Seq(SerializeUtils.toJson(Seq(c))))
      if (checkTransactionPool()) {
        ctx.getTransactionPool.addTransactionToCache(transaction)
        ctx.getCustomBroadcastHandler.PublishOfCustom(context, mediator, Topic.Transaction, transaction)
        RepLogger.System_Logger.info(s"addTransactionToCache:${transaction.id}")
      } else {
        return 6
      }
    }
    0
  }

  def checkTransactionPool(): Boolean = {
    var count = 0
    var b = false
    breakable {
      while (count < 3) {
        if (!ctx.getTransactionPool.hasOverflowed) {
          b = true
          break
        } else {
          count = count + 1
          Thread.sleep(5000)
        }
      }
    }
    b
  }

  def autoCensor(src: String): (Int, String) = {
    try {
      val result: JSONObject = client.get.textCensorUserDefined(src)
      if (result.has("error_msg")) { //审核失败，直接返回
        val error_msg = result.getString("error_msg")
        RepLogger.info(RepLogger.Business_Logger, s"censor failed. error_msg=$error_msg")
        return (-1, error_msg)
      }
      if (result.has("conclusionType")) {
        result.getInt("conclusionType") match {
          case 1 =>
            (1, "合规")
          case 2 =>
            val msg = result.getJSONArray("data").get(0).asInstanceOf[JSONObject].getString("msg")
            (2, msg)
          case 3 =>
            (3, "疑似") // 疑似要不要屏蔽？通知人工审核？
          case 4 =>
            (4, "审核失败")
        }
      } else {
        (5, "未知")
      }
    } catch {
      case e: Exception =>
        RepLogger.System_Logger.info(s"autoCensor exception:${e.getMessage}")
        (-2, e.getMessage)
    }
  }

  def getAndCensorBlock(): Unit = {
    val currentChainHeight = blockSearcher.getChainInfo.height
    if (currentChainHeight > censoredHeight.longValue()) {
      val start = censoredHeight.longValue()
      breakable(
        for (i <- start + 1 to currentChainHeight) {
          val block = blockSearcher.getBlockByHeight(i).get
          val code = censorBlock(block)
          if (code == 0) {
            censoredHeight.increment()
          } else {
            break
          }
        }
      )
    } else {
      RepLogger.System_Logger.info(s"currentChainHeight=$currentChainHeight, censoredHeight=${censoredHeight.longValue()},currentChainHeight <= censoredHeight,ignore")
    }
  }

  private def checkPermission(): Boolean = {
    if (!hashPermission) {
      val certId = IdTool.getCertIdFromName(ctx.getSystemName)
      val creditCode = ctx.getConfig.getChainNetworkId + IdTool.DIDPrefixSeparator + certId.creditCode
      val opName = ctx.getConfig.getChainNetworkId + IdTool.DIDPrefixSeparator + "RegulateTPL.regBlocks"
      hashPermission = pv.CheckPermission(creditCode, certId.certName, opName, null)
    }
    hashPermission
  }

  override def preStart(): Unit = {
    RepLogger.System_Logger.info(s"$sysTag ~ AICensor started!")
    scheduler.scheduleOnce(60.seconds, self, AICensor)
  }

  override def receive = {
    case AICensor =>
      RepLogger.System_Logger.debug("AICensor received msg!")
      if (consensusCondition.CheckWorkConditionOfSystem(pe.getRepChainContext.getNodeMgr.getStableNodes.size)) {
        if (checkPermission()) {
          getAndCensorBlock()
        } else {
          RepLogger.System_Logger.info("权限验证失败，本次审查结束！")
        }
      } else {
        RepLogger.System_Logger.info("当前系统达不到共识要求，暂不审查！")
      }
      scheduler.scheduleOnce(interval.seconds, self, AICensor)
  }
}
