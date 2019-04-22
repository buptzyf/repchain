/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
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

import akka.actor.{Actor, Address,Props}
import akka.cluster.ClusterEvent._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.{Cluster, MemberStatus}
import rep.app.conf.TimePolicy
import rep.app.conf.SystemProfile
import rep.network.Topic
import rep.network.cluster.MemberListener.{ Recollection}
import rep.network.tools.PeerExtension
import rep.utils.GlobalUtils.{ActorType,EventType}
import rep.utils.{ TimeUtils}
import org.slf4j.LoggerFactory
import scala.collection.mutable.HashMap
import rep.network.base.ModuleBase
import rep.network.sync.SyncMsg.StartSync
import scala.util.control.Breaks._
import scala.collection.mutable.ArrayBuffer
import rep.log.RepLogger
import rep.protos.peer.Event

/**
  * Cluster节点状态监听模块
  *
  * @author shidianyue
  * @version 1.0
  * @update 2018-05 jiangbuyun
  **/
object MemberListener {
  def props(name: String): Props = Props(classOf[MemberListener], name)
  //稳定节点回收请求
  case object Recollection

}
/**
  * Cluster节点状态监听类
  *
  * @author shidianyue
  * @version 1.0
  * @since 1.0
  **/

class MemberListener(MoudleName:String) extends ModuleBase(MoudleName) with ClusterActor {

  import context.dispatcher

  import scala.concurrent.duration._

  protected def log = LoggerFactory.getLogger(this.getClass)
  
  val addr_self = akka.serialization.Serialization.serializedActorPath(self)

  val cluster = Cluster(context.system)

  var preloadNodesMap = HashMap[ Address, (Long,String) ]()

  //def scheduler = context.system.scheduler


  override def preStart(): Unit =
    super.preStart()

  cluster.subscribe(self, classOf[ MemberEvent ])
  context.system.eventStream.subscribe(self, classOf[ akka.remote.DisassociatedEvent ])

  SubscribeTopic(mediator, self, addr_self, Topic.Event, false)

  /**
    * 节点状态是否稳定
    * @param srcTime
    * @param dur
    * @return
    */
  def isStableNode(srcTime: Long, dur: Long): Boolean = {
    (TimeUtils.getCurrentTime() - srcTime) > dur
  }


  override def postStop(): Unit =
    cluster unsubscribe self

  //无序，暂时为动态的第一个（可变集合是否是安全的，因为并不共享。如果多个System会共存副本的话，同样需要验证一致性）
  //必须缓存，如果memActor跪了则每次出块就会出问题
  //同步的时候一定要把nodes也同步
  var nodes = Set.empty[ Address ]
  

  private def isCandidatorNode(roles: Set[String]):Boolean = {
    var r = false
    breakable(
    roles.foreach(f=>{
      if(f.startsWith("CRFD-Node")){
        r = true
        break
      }
    })
    )
    r
  }
  
  private def getNodeName(roles: Set[String]):String = {
    var r = ""
    breakable(
    roles.foreach(f=>{
      if(f.startsWith("CRFD-Node")){
        r = f.substring(f.indexOf("CRFD-Node")+10)
        break
      }
    })
    )
    r
  }
  
  def receive = {

    //系统初始化时状态
    case state: CurrentClusterState =>
      RepLogger.trace(RepLogger.System_Logger, this.getLogMsgPrefix("Member call first time"))
      //nodes = state.members.collect {
      //  case m if m.status == MemberStatus.Up => m.address
      //}
      var snodes  = new ArrayBuffer[(Address ,String)]()
      state.members.foreach(m=>{
        if (m.status == MemberStatus.Up){
          nodes += m.address
          if(this.isCandidatorNode(m.roles)){
            snodes.append((m.address,this.getNodeName(m.roles)))
            sendEvent(EventType.PUBLISH_INFO, mediator, this.getNodeName(m.roles), Topic.Event, Event.Action.MEMBER_UP)
          }
        }
      })
      
      pe.getNodeMgr.resetNodes(nodes)
      pe.getNodeMgr.resetStableNodes(snodes.toSet)
      //成员入网
    case MemberUp(member) =>
      nodes += member.address
      RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix("Member is Up: {}. {} nodes in cluster"+"~"+member.address+"~"+nodes.size))
      pe.getNodeMgr.putNode(member.address)
      if(member.roles != null && !member.roles.isEmpty && this.isCandidatorNode(member.roles)){
        preloadNodesMap.put(member.address, (TimeUtils.getCurrentTime(),this.getNodeName(member.roles)))
        sendEvent(EventType.PUBLISH_INFO, mediator, this.getNodeName(member.roles), Topic.Event, Event.Action.MEMBER_UP)
      }
      
      scheduler.scheduleOnce(TimePolicy.getSysNodeStableDelay millis,
        self, Recollection)
        
      //成员离网
    case MemberRemoved(member, _) =>
      nodes -= member.address
      //log.info("Member is Removed: {}. {} nodes cluster",
        //member.address, nodes.size)
      RepLogger.info(RepLogger.System_Logger,  "Member is Removed: {}. {} nodes cluster"+"~"+member.address+"~"+nodes.size)
      preloadNodesMap.remove(member.address)
      pe.getNodeMgr.removeNode(member.address)
      pe.getNodeMgr.removeStableNode(member.address)
      sendEvent(EventType.PUBLISH_INFO, mediator, this.getNodeName(member.roles), Topic.Event, Event.Action.MEMBER_DOWN)
      

   
      //稳定节点收集
    case Recollection =>
      Thread.sleep(TimePolicy.getStableTimeDur) //给一个延迟量
      RepLogger.trace(RepLogger.System_Logger, this.getLogMsgPrefix(" MemberListening recollection"))
      preloadNodesMap.foreach(node => {
        if (isStableNode(node._2._1, TimePolicy.getSysNodeStableDelay)) {
          pe.getNodeMgr.putStableNode(node._1,node._2._2)
          if(pe.getNodeMgr.getStableNodes.size >= SystemProfile.getVoteNoteMin){
            //组网成功之后开始系统同步
            pe.getActorRef(ActorType.synchrequester) ! StartSync(true)
          }
        }
      })
      if (preloadNodesMap.size > 0) pe.getNodeMgr.getStableNodes.foreach(node => {
        if (preloadNodesMap.contains(node)) preloadNodesMap.remove(node)
      })
      if (preloadNodesMap.size > 0) self ! Recollection

    case event: akka.remote.DisassociatedEvent => //ignore
      nodes -= event.remoteAddress
      log.info("Member is Removed: {}. {} nodes cluster", event.remoteAddress, nodes.size)
      preloadNodesMap.remove(event.remoteAddress)
      pe.getNodeMgr.removeNode(event.remoteAddress)
      pe.getNodeMgr.removeStableNode(event.remoteAddress)
      
   
    case MemberLeft(member) => //ignore
      nodes -= member.address
      //log.info("Member is Removed: {}. {} nodes cluster",
      //  member.address, nodes.size)
      RepLogger.info(RepLogger.System_Logger, "Member is Removed: {}. {} nodes cluster"+"~"+member.address+"~"+nodes.size)
      preloadNodesMap.remove(member.address)
      pe.getNodeMgr.removeNode(member.address)
      pe.getNodeMgr.removeStableNode(member.address)
      
      
    case MemberExited(member) => //ignore
      nodes -= member.address
      //log.info("Member is Removed: {}. {} nodes cluster",
      //  member.address, nodes.size)
      RepLogger.info(RepLogger.System_Logger,  "Member is Removed: {}. {} nodes cluster"+"~"+member.address+"~"+nodes.size)
      preloadNodesMap.remove(member.address)
      pe.getNodeMgr.removeNode(member.address)
      pe.getNodeMgr.removeStableNode(member.address)
      

    case _: MemberEvent => // ignore
  }
}