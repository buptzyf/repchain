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

package rep.network.consensus.transaction

import akka.actor.{ Actor, ActorRef, Props, Address, ActorSelection }
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.routing._;
import rep.app.conf.{ SystemProfile, TimePolicy }
import rep.network.base.ModuleBase
import rep.network.tools.PeerExtension
import rep.network.Topic
import rep.protos.peer._
import rep.utils.GlobalUtils.{ EventType }
import rep.utils._
import scala.collection.mutable._
import rep.network.consensus.util.BlockVerify
import scala.util.control.Breaks
import rep.network.util.NodeHelp
import rep.network.consensus.util.BlockHelp
import rep.network.consensus.util.BlockVerify
import rep.log.RepLogger
import rep.log.RepTimeTracer
import rep.network.consensus.block.Blocker.{ PreTransBlock}


object DispatchOfPreload  {
  def props(name: String): Props = Props(classOf[DispatchOfPreload], name)
}


class DispatchOfPreload(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.concurrent.duration._
  import scala.collection.immutable._

  private var router: Router = null

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "DispatchOfPreload Start"))
  }

  private def createRouter = {
    if (router == null) {
      var len = SystemProfile.getVoteNodeList.size()
      if(len <= 0){
        len  = 1
      }
      var list: Array[Routee] = new Array[Routee](len)
      for (i <- 0 to len-1 ) {
        var ca = context.actorOf(PreloaderForTransaction.props("preloaderoftransaction" + i), "preloaderoftransaction" + i)
        context.watch(ca)
        list(i) = new ActorRefRoutee(ca)
      }
      val rlist: IndexedSeq[Routee] = list.toIndexedSeq
      router = Router(SmallestMailboxRoutingLogic(), rlist)
    }
  }

  

  override def receive = {
    case PreTransBlock(block,prefixOfDbTag) =>
      createRouter
      router.route(PreTransBlock(block,prefixOfDbTag) , sender)  
    case _ => //ignore
  }
}