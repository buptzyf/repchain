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

package rep.network.consensus.cfrd.endorse

import akka.actor.{Actor, ActorRef, ActorSelection, Address, Props}
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.routing._
import rep.app.conf.{SystemProfile, TimePolicy}
import rep.network.base.ModuleBase
import rep.network.tools.PeerExtension
import rep.protos.peer._
import rep.utils.GlobalUtils.EventType
import rep.utils._

import scala.collection.mutable._
import rep.network.consensus.util.BlockVerify

import scala.util.control.Breaks
import rep.network.util.NodeHelp
import rep.network.consensus.util.BlockHelp
import rep.network.consensus.util.BlockVerify
import rep.log.RepLogger
import rep.log.RepTimeTracer
import rep.network.autotransaction.Topic
import rep.network.consensus.cfrd.MsgOfCFRD.EndorsementInfo

/**
 * Created by jiangbuyun on 2020/03/19.
 * 接收并分派背书请求actor
 */

object DispatchOfRecvEndorsement {
  def props(name: String): Props = Props(classOf[DispatchOfRecvEndorsement], name)
}


class DispatchOfRecvEndorsement(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.concurrent.duration._
  import scala.collection.immutable._

  private var router: Router = null

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "DispatchOfRecvEndorsement Start"))
  }

  private def createRouter = {
    if (router == null) {
      var list: Array[Routee] = new Array[Routee](SystemProfile.getVoteNodeList.size())
      for (i <- 0 to SystemProfile.getVoteNodeList.size() - 1) {
        var ca = context.actorOf(Endorser4Future.props("endorser" + i), "endorser" + i)
        context.watch(ca)
        list(i) = new ActorRefRoutee(ca)
      }
      val rlist: IndexedSeq[Routee] = list.toIndexedSeq
      router = Router(SmallestMailboxRoutingLogic(), rlist)
    }
  }

  

  override def receive = {
    case EndorsementInfo(block, blocker) =>
      createRouter
      router.route(EndorsementInfo(block, blocker), sender)  
    case _ => //ignore
  }
}