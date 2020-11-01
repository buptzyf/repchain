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

package rep.network.cluster

import akka.actor.{Actor, ActorRef}
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}
import rep.protos.peer.Event
import rep.utils.ActorUtils
import rep.utils.GlobalUtils.EventType
import rep.app.conf.SystemProfile
import rep.network.autotransaction.Topic

/**
  * Akka组网类
  *
  * @author shidianyue
  * @version 1.0
  **/
 trait ClusterActor extends  Actor{
  import akka.cluster.pubsub.DistributedPubSub

  val mediator = DistributedPubSub(context.system).mediator

  /**
    * 根据全网节点的地址（带IP）判断是否属于同一个System
    *
    * @param src
    * @param tar
    * @return
    */
  def isThisAddr(src: String, tar: String): Boolean = {
    src.startsWith(tar)
  }

  /**
    * 广播Event消息
    *
    * @param eventType 发送、接受
    * @param mediator
    * @param addr
    * @param topic
    * @param action
    */
  def sendEvent(eventType: Int, mediator: ActorRef, addr: String, topic: String, action: Event.Action): Unit = {
    if(SystemProfile.getRealtimeGraph == 1){
      eventType match {
        case EventType.PUBLISH_INFO =>
          //publish event(send message)
          val evt = new Event(addr, topic,
            action)
          mediator ! Publish(Topic.Event, evt)
        case EventType.RECEIVE_INFO =>
          //receive event
          val evt = new Event(topic, addr,
            action)
          mediator ! Publish(Topic.Event, evt)
      }
    }
  }
  
  
    /**
    * 广播SyncEvent消息
    *
    * @param eventType 发送、接受
    * @param mediator
    * @param fromAddr
    * @param toAddr
    * @param action
    */
  def sendEventSync(eventType: Int, mediator: ActorRef, fromAddr: String, toAddr: String, action: Event.Action): Unit = {
    eventType match {
      case EventType.PUBLISH_INFO =>
        //publish event(send message)
        val evt = new Event(fromAddr, toAddr,
          action)
        mediator ! Publish(Topic.Event, evt)
      case EventType.RECEIVE_INFO =>
        //receive event
        val evt = new Event(fromAddr, toAddr,
          action)
        mediator ! Publish(Topic.Event, evt)
    }

  }

  /**
    * 获取有完全信息的地址（ip和port）
    * @param ref
    * @return
    */
  def getClusterAddr(ref:ActorRef):String = {
    akka.serialization.Serialization.serializedActorPath(ref)
  }

  /**
    * cluster订阅消息
    *
    * @param mediator
    * @param self
    * @param addr
    * @param topic
    * @param isEvent
    */
  def SubscribeTopic(mediator: ActorRef, self: ActorRef, addr: String, topic: String, isEvent: Boolean) = {
    mediator ! Subscribe(topic, self)
    //广播本次订阅事件
    if (isEvent) sendEvent(EventType.PUBLISH_INFO, mediator, addr, Topic.Event, Event.Action.SUBSCRIBE_TOPIC)
  }

}
