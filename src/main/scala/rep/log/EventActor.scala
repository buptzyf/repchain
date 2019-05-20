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

import rep.network.Topic
import rep.protos.peer._
import akka.stream.actor._
import akka.actor.{Props,Address,PoisonPill}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus
import rep.ui.web.EventServer
import rep.network.tools.PeerExtension
import rep.storage._
import akka.stream.Graph

/** 负责处理websocket事件订阅的actor伴生对象
 * @author c4w
 */
object EventActor {
  def props: Props = Props[EventActor]
}

/** 负责处理websocket事件订阅的Actor，一方面作为消费者在后台订阅Event,另一方面作为WebSocket的source
 *  向浏览器push事件流, 事件流采用protobuf流
 * @author c4w
 * @constructor
 */
class EventActor extends ActorPublisher[Event] {
  import scala.concurrent.duration._
  
  val cluster = Cluster(context.system)
  var nodes = Set.empty[Address]
  var buffer = Vector.empty[Event]

  /** 启动,订阅集群入网、离网事件,订阅Topic事件
   * 
   */
  override def preStart(): Unit ={
    cluster.subscribe(self, classOf[MemberEvent])
    val mediator = DistributedPubSub(context.system).mediator
    //发送订阅Event
    mediator ! Subscribe(Topic.Event, self)   
    //发送当前出块人
    val pe = PeerExtension(context.system)
    self ! new Event( pe.getBlocker.blocker, "", Event.Action.CANDIDATOR) 
    val ref = context.actorSelection("/user/modulemanager/memberlistener")
    if(ref != null) ref ! cluster.state
  }

  /** 停止处理，取消订阅
   * 
   */
  override def postStop(): Unit =
    cluster unsubscribe self
    
  /** 接收Event处理，支持所谓“背压”方式，即根据web端消费能力push
   *  
   */
  override def receive: Receive = {
    //Topic事件
    case evt:Event=> 
      //当浏览器关掉，自杀
      if(this.isCanceled){
        self ! PoisonPill
      }else{
        if (buffer.isEmpty && totalDemand > 0) {
          onNext(evt)
        }
        else {
          buffer :+= evt
          if (totalDemand > 0) {
            val (use,keep) = buffer.splitAt(totalDemand.toInt)
            buffer = keep
            use foreach onNext
          }
        }  
      }
    //集群事件
    /*case state: CurrentClusterState =>
      val iter = state.members.iterator;
      iter.foreach { m =>
        if (m.status == MemberStatus.Up){
          self ! new Event( m.address.toString, "", Event.Action.MEMBER_UP)
        }
      }
    //节点入网
    case MemberUp(member) =>
      nodes += member.address
      self !new Event( member.address.toString, "", Event.Action.MEMBER_UP)
    case UnreachableMember(member) =>
    //节点离网
    case MemberRemoved(member, _) =>
      val maddr = member.address.toString
      val saddr =  Cluster(context.system).selfAddress.toString
      //println(s"-------$maddr-----$saddr")
      if(maddr == saddr){
        context.system.terminate()
      }else{
        nodes -= member.address
        self ! new Event( member.address.toString,"",Event.Action.MEMBER_DOWN)
      }
    case _: MemberEvent => // ignore
    */
  }
}

