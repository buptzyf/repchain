/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Fintech Research Center of ISCAS.
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
 */

package rep.network.tools.Statistic

import akka.actor.Actor
import akka.actor.Actor.Receive
import akka.cluster.pubsub.DistributedPubSubMediator.Subscribe
import rep.app.conf.TimePolicy
import rep.network.Topic
import rep.network.consensus.block.BlockModule.ConfirmedBlock
import rep.network.tools.PeerExtension
import rep.network.tools.Statistic.StatisticCollection.{PerformanceCollection, TPSCollection}
import rep.utils.RepLogging.LogTime
import rep.utils.{RepLogging, TimeUtils}

/**
  * Created by shidianyue on 2017/9/26.
  */
object StatisticCollection {

  case object TPSCollection

  case object PerformanceCollection

}

class StatisticCollection extends Actor with RepLogging {

  import akka.cluster.pubsub.DistributedPubSub
  import context.dispatcher
  import scala.concurrent.duration._


  val mediator = DistributedPubSub(context.system).mediator

  var schedulerLink: akka.actor.Cancellable = null

  val moduleName = "Statistic"

  val pe = PeerExtension(context.system)

  var count = 0l

  var secondCount = 0l

  var isReady = false

  var totalTranCount = 0l

  var totalTranSize = 0l
  var totalBlkSize = 0l
  var totalBlkCount = 0l
  var totalResultSize = 0l

  def scheduler = context.system.scheduler

  def clearSched() = {
    if (schedulerLink != null) schedulerLink.cancel()
    null
  }

  override def preStart(): Unit = {
    logMsg(LOG_TYPE.INFO, moduleName, "Statistic module start", "")
    //TODO kami 这里值得注意：整个订阅过s程也是一个gossip过程，并不是立即生效。需要等到gossip一致性成功之后才能够receive到注册信息。
    mediator ! Subscribe(Topic.Block, self)
    scheduler.scheduleOnce(1 seconds, self, TPSCollection)
    scheduler.scheduleOnce(10 seconds, self, PerformanceCollection)
  }

  override def receive: Receive = {

    case ConfirmedBlock(blk, height, actRef) =>
      if (!isReady) isReady = true
      count = count + blk.transactions.size
      totalBlkCount += 1
      totalTranCount += blk.transactions.size
      totalBlkSize += blk.toByteArray.size
      blk.nonHashData.get.transactionResults.foreach(trans => totalResultSize += trans.toByteArray.size)
      blk.transactions.foreach(trans => totalTranSize += trans.toByteArray.size)


    case TPSCollection =>
      logMsg(LOG_TYPE.INFO, moduleName, s"Statistic instant TPS is $count in ${TimeUtils.getCurrentTime()} " +
        s" ~ Rest Trans is ${pe.getTransLength()}", "")
      if (isReady) {
        secondCount += 1
        logMsg(LOG_TYPE.INFO, moduleName, s"Statistic AVG TPS is ${totalTranCount / secondCount} " +
          s"in ${TimeUtils.getCurrentTime()} ~ Rest Trans is ${pe.getTransLength()}", "")
      }
      count = 0
      scheduler.scheduleOnce(1 seconds, self, TPSCollection)

    case PerformanceCollection =>
      if (isReady) {
        logMsg(LOG_TYPE.INFO, moduleName, s"Statistic AVG Blk（exclusive trans and trans result） Size  is ${(totalBlkSize - totalTranSize - totalResultSize) / totalBlkCount} in ${TimeUtils.getCurrentTime()}", "")
        logMsg(LOG_TYPE.INFO, moduleName, s"Statistic AVG Trans Size  is ${totalTranSize / totalTranCount} in ${TimeUtils.getCurrentTime()}", "")
        logMsg(LOG_TYPE.INFO, moduleName, s"Statistic AVG Trans Result Size  is ${totalResultSize / totalTranCount} in ${TimeUtils.getCurrentTime()}", "")
      }
      scheduler.scheduleOnce(10 seconds, self, PerformanceCollection)

    case lt: LogTime =>
      logTime(lt.module, lt.msg, lt.time, lt.cluster_addr)
  }
}
