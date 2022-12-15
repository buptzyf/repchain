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

package rep.log

import akka.actor.{Actor, ActorRef, Props, Terminated}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Subscribe
import akka.cluster.Cluster
import akka.cluster.MemberStatus
import rep.network.tools.PeerExtension

import scala.collection.mutable.HashSet
import rep.log.RecvEventActor.Register
import rep.network.autotransaction.Topic
import rep.network.base.ModuleBase
import rep.network.cluster.ClusterActor
import rep.network.util.NodeHelp
import rep.proto.rc2.Event

object RecvEventActor {
  def props(name: String): Props = Props(classOf[RecvEventActor], name)

  final case class Register(actorRef: ActorRef)

}

class RecvEventActor(MoudleName: String)  extends ModuleBase(MoudleName) with ClusterActor {
  var stageActor: ActorRef = null
  var stageActors: HashSet[ActorRef] = HashSet[ActorRef]()
  val cluster = Cluster(context.system)

  override def preStart(): Unit = {
    if (pe.getRepChainContext.getConsensusNodeConfig.getVoteListOfConfig.contains(pe.getSysTag)) {
      //共识节点可以订阅交易的广播事件
      if (pe.getRepChainContext.getConfig.useCustomBroadcast) {
        pe.getRepChainContext.getCustomBroadcastHandler.SubscribeTopic(Topic.Event, "/user/RecvEventActor")
        RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix("Subscribe custom broadcast,/user/RecvEventActor"))
      } else {
        val mediator = DistributedPubSub(context.system).mediator
        mediator ! Subscribe(Topic.Event, self)
        RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix("Subscribe system broadcast,/user/RecvEventActor"))
      }
    }
  }

  private def clusterInfo(stageActor: ActorRef) = {
    cluster.state.members.foreach(m => {
      if (m.status == MemberStatus.Up && NodeHelp.isCandidateNow(NodeHelp.getNodeNameFromRoles(m.roles),pe.getRepChainContext.getConsensusNodeConfig.getVoteListOfConfig.toSet)) {
        stageActor ! new Event(NodeHelp.getNodeNameFromRoles(m.roles), Topic.Event, Event.Action.MEMBER_UP)
      }
    })
  }

  override def receive = {
    case Register(actorRef) => {
      //this.stageActor = actorRef
      this.stageActors.add(actorRef)
      context.watch(actorRef)
      clusterInfo(actorRef)
      val pe = PeerExtension(context.system)
      self ! new Event(pe.getBlocker.blocker, "", Event.Action.CANDIDATOR)
    }

    case Terminated(actorRef) => {
      //this.stageActor = null
      this.stageActors.remove(actorRef)
      context.unwatch(actorRef)

      //context.stop(self)
    }

    case evt: Event => {
      //if(this.stageActor != null) this.stageActor ! evt
      this.stageActors.foreach(f => {
        f ! evt
      })
    }
  }
}

