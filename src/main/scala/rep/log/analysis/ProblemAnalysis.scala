package rep.log.analysis

import com.googlecode.concurrentlinkedhashmap.{ConcurrentLinkedHashMap, Weighers}
import rep.app.system.RepChainSystemContext
import rep.log.RepLogger
import rep.log.analysis.ProblemAnalysis.{ProblemAccess}
import rep.log.httplog.AlertInfo

object ProblemAnalysis{
  case class ProblemAccess(source:String,time:Long,count:Int)
}
class ProblemAnalysis(val ctx:RepChainSystemContext) {
  private val cacheMaxSize = 10000
  private val thresholdOfProblemTrading = 10 //每秒出现20条问题交易，就报警
  private val thresholdOfDownBlocks = 100 //每秒下载100个区块，就报警
  final private implicit val pt = new ConcurrentLinkedHashMap.Builder[String, Option[ProblemAccess]]()
    .maximumWeightedCapacity(cacheMaxSize)
    .weigher(Weighers.singleton[Option[ProblemAccess]]).build

  private def addQuestionAccess(source:String,threshold:Int,unit:Int,interval:Int,category:String,error:String):Unit={
    if (pt.containsKey(source)) {
      val info = pt.get(source).get
      val t = System.currentTimeMillis() - info.time
      if (t > interval * unit) { //间隔interval重置统计
        val info = ProblemAccess(source, System.currentTimeMillis(), 1)
        pt.put(source, Some(info))
      } else {
        val c = ((info.count + 1) / t * unit) > threshold
        if (c) {
          //报警
          RepLogger.sendAlertToDB(ctx.getHttpLogger(),
            new AlertInfo(category, 2,
              s"Node Name=${ctx.getSystemName},source Url= ${source}," +
                s"erroInfo=${info.count + 1}${error}")
          )
          pt.remove(source)
        } else {
          val info1 = ProblemAccess(info.source, info.time, info.count + 1)
          pt.put(source, Some(info1))
        }
      }
    } else {
      val info = ProblemAccess(source, System.currentTimeMillis(), 1)
      pt.put(source, Some(info))
    }
  }
  def AddProblemTrading(source:String):Unit={
    val key = "tx-"+source
    this.addQuestionAccess(key,this.thresholdOfProblemTrading,
      1000,30,"API","/s次地提交重复或者无效交易，请管理员核实并拒绝该URL的请求.")
  }

  def AddMaliciousDownloadOfBlocks(source: String): Unit = {
    val key = "downBlock-" + source
    this.addQuestionAccess(key, this.thresholdOfDownBlocks,
      1000,30,"API","/s次地下载区块，请管理员核实并拒绝该URL的请求.")
  }

  def AddNodeFailure(nodeName:String,url:String):Unit={
    RepLogger.sendAlertToDB(ctx.getHttpLogger(),
      new AlertInfo("NETWORK", 2,
        s"Report node=${ctx.getSystemName},Failure node= ${url}," +
          s"erroInfo=${nodeName}发生离网事件，请管理员核实并处理.")
    )
  }

  def AddBlockFailure(nodeName: String, url: String): Unit = {
    RepLogger.sendAlertToDB(ctx.getHttpLogger(),
      new AlertInfo("CONSENSUS", 2,
        s"Report node=${ctx.getSystemName},Failure node= ${url}," +
          s"erroInfo=${nodeName}发生出块失败事件，请管理员核实并处理.")
    )
  }

  def AddMaliciousNodeDownloadOfBlocks(source: String): Unit = {
    val key = "nodeDownBlock-" + source
    this.addQuestionAccess(key, this.thresholdOfDownBlocks,
      1000, 30, "NETWORK","/s次地下载区块，请管理员核实并拒绝该URL的请求.")
  }

}
