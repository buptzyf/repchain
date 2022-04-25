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
import akka.cluster.pubsub.DistributedPubSubMediator.{ Subscribe}
import akka.cluster.Cluster
import akka.cluster.MemberStatus
import rep.network.tools.PeerExtension
import scala.collection.mutable.{HashSet}
import rep.log.RecvEventActor.Register
import rep.network.autotransaction.Topic
import rep.network.util.NodeHelp
import rep.proto.rc2.Event

object RecvEventActor {
  def props: Props = Props[RecvEventActor]

  final case class Register(actorRef: ActorRef)

}

class RecvEventActor extends Actor {
  var stageActor: ActorRef = null
  var stageActors: HashSet[ActorRef] = HashSet[ActorRef]()
  val cluster = Cluster(context.system)

  override def preStart(): Unit = {
    val mediator = DistributedPubSub(context.system).mediator
    mediator ! Subscribe(Topic.Event, self)
  }

  private def clusterInfo(stageActor: ActorRef) = {
    cluster.state.members.foreach(m => {
      if (m.status == MemberStatus.Up && NodeHelp.isCandidatorNode(m.roles)) {
        stageActor ! new Event(NodeHelp.getNodeName(m.roles), Topic.Event, Event.Action.MEMBER_UP)
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

