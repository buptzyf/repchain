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

import akka.actor.{Actor, Address, Props}
import akka.cluster.ClusterEvent._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.{Cluster, MemberStatus}
import rep.app.conf.TimePolicy
import rep.app.conf.SystemProfile
import rep.network.cluster.MemberListener.Recollection
import rep.network.module.cfrd.CFRDActorType
import rep.utils.GlobalUtils.EventType
import rep.utils.TimeUtils
import org.slf4j.LoggerFactory

import scala.collection.mutable.HashMap
import rep.network.base.ModuleBase
import rep.network.sync.SyncMsg.StartSync

import scala.util.control.Breaks._
import scala.collection.mutable.ArrayBuffer
import rep.log.RepLogger
import rep.protos.peer.Event
import rep.network.util.NodeHelp
import rep.app.RepChainMgr
import rep.network.autotransaction.Topic
import rep.network.consensus.byzantium.ConsensusCondition

/**
 * Cluster节点状态监听模块
 *
 * @author shidianyue
 * @version 1.0
 * @update 2018-05 jiangbuyun
 */
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
 */

class MemberListener(MoudleName: String) extends ModuleBase(MoudleName) with ClusterActor {

  import context.dispatcher

  import scala.concurrent.duration._

  protected def log = LoggerFactory.getLogger(this.getClass)

  val addr_self = akka.serialization.Serialization.serializedActorPath(self)

  val cluster = Cluster(context.system)

  var preloadNodesMap = HashMap[Address, (Long, String)]()

  private var isStartSynch = false

  override def preStart(): Unit =
    super.preStart()

  cluster.subscribe(self, classOf[MemberEvent])
  cluster.subscribe(self,classOf[ClusterDomainEvent])
  //context.system.eventStream.subscribe(self, classOf[akka.remote.DisassociatedEvent])

  //SubscribeTopic(mediator, self, addr_self, Topic.Event, false)

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

  def receive = {

    //系统初始化时状态
    case state: CurrentClusterState =>
      RepLogger.trace(RepLogger.System_Logger, this.getLogMsgPrefix("Member call first time"))
      var nodes = Set.empty[Address]
      var snodes = new ArrayBuffer[(Address, String)]()
      state.members.foreach(m => {
        if (m.status == MemberStatus.Up) {
          nodes += m.address
          if (NodeHelp.isCandidatorNode(m.roles)) {
            snodes.append((m.address, NodeHelp.getNodeName(m.roles)))
            RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix(s"CurrentClusterState: nodes is candidator,node name =${NodeHelp.getNodeName(m.roles)}"))
            sendEvent(EventType.PUBLISH_INFO, mediator, NodeHelp.getNodeName(m.roles), Topic.Event, Event.Action.MEMBER_UP)
          } else {
            RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix(s"CurrentClusterState: nodes is candidator,node name =${m.address.toString}"))
          }
        }
      })
      pe.getNodeMgr.resetNodes(nodes)
      pe.getNodeMgr.resetStableNodes(snodes.toSet)

    //成员入网
    case MemberUp(member) =>
      RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix("Member is Up: {}. {} nodes in cluster" + "~" + member.address + "~" + pe.getNodeMgr.getNodes.mkString("|")))
      pe.getNodeMgr.putNode(member.address)
      if (member.roles != null && !member.roles.isEmpty && NodeHelp.isCandidatorNode(member.roles)) {
        preloadNodesMap.put(member.address, (TimeUtils.getCurrentTime(), NodeHelp.getNodeName(member.roles)))
        RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix(s"Member is Up:  nodes is condidator,node name=${NodeHelp.getNodeName(member.roles)}"))
        sendEvent(EventType.PUBLISH_INFO, mediator, NodeHelp.getNodeName(member.roles), Topic.Event, Event.Action.MEMBER_UP)
      } else {
        RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix(s"Member is Up:  nodes is not condidator,node address=${member.address.toString}"))
      }
      schedulerLink = scheduler.scheduleOnce((
        //TimePolicy.getSysNodeStableDelay +
          TimePolicy.getStableTimeDur).millis, self, Recollection)
    //稳定节点收集
    case Recollection =>
      schedulerLink = clearSched()
      RepLogger.trace(RepLogger.System_Logger, this.getLogMsgPrefix(" MemberListening recollection"))
      preloadNodesMap.foreach(node => {
        if (isStableNode(node._2._1, TimePolicy.getSysNodeStableDelay)) {
          pe.getNodeMgr.putStableNode(node._1, node._2._2)
        } else {
          RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix(s"Recollection:  nodes not stable,node name=${node._2._2}"))
        }
      })
      
      if (preloadNodesMap.size > 0) {
        pe.getNodeMgr.getStableNodes.foreach(node => {
          if (preloadNodesMap.contains(node)) {
            preloadNodesMap.remove(node)
            RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix(s"Recollection: clear preloadnodemap,node=${node}"))
          }
        })
      }

      if (!this.isStartSynch) {
        if (ConsensusCondition.CheckWorkConditionOfSystem(pe.getNodeMgr.getStableNodes.size)) {
          //组网成功之后开始系统同步
          RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix(s"Recollection:  system startup ,start sync,node name=${pe.getSysTag}"))
          pe.getActorRef(CFRDActorType.ActorType.synchrequester) ! StartSync(true)
          this.isStartSynch = true
        } else {
          RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix(s"Recollection:  nodes less ${SystemProfile.getVoteNodeMin},node name=${pe.getSysTag}"))
        }
      } else {
        RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix(s"Recollection:  local consensus start finish,node name=${pe.getSysTag}"))
      }

      if (preloadNodesMap.size > 0) {
        //self ! Recollection
        schedulerLink = scheduler.scheduleOnce((
          TimePolicy.getStableTimeDur/5).millis, self, Recollection)
      }

    //成员离网
    case MemberRemoved(member, _) =>
      RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix("Member is Removed: {}. {} nodes cluster" + "~" + member.address))

      System.err.println(s"MemberRemoved:printer=${pe.getSysTag} ~~ removed=${pe.getNodeMgr.getNodeName4AddrString(member.address.toString)}")

      val tmp = pe.getNodeMgr.getNodeName4AddrString(member.address.toString)
      if(tmp.equals(pe.getSysTag)){
        RepChainMgr.ReStart(pe.getSysTag)
      }

      preloadNodesMap.remove(member.address)
      pe.getNodeMgr.removeNode(member.address)
      pe.getNodeMgr.removeStableNode(member.address)
      sendEvent(EventType.PUBLISH_INFO, mediator, NodeHelp.getNodeName(member.roles), Topic.Event, Event.Action.MEMBER_DOWN)



    /*case event: akka.remote.DisassociatedEvent => //ignore
      RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix("DisassociatedEvent: {}. {} nodes cluster" + "~" + event.remoteAddress.toString))
      preloadNodesMap.remove(event.remoteAddress)
      pe.getNodeMgr.removeNode(event.remoteAddress)
      pe.getNodeMgr.removeStableNode(event.remoteAddress)*/
    case MemberLeft(member) => //ignore
      RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix("MemberLeft: {}. {} nodes cluster" + "~" + member.address.toString))
      /*preloadNodesMap.remove(member.address)
      pe.getNodeMgr.removeNode(member.address)
      pe.getNodeMgr.removeStableNode(member.address)*/

    case MemberExited(member) => //ignore
      RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix("MemberExited: {}. {} nodes cluster" + "~" + member.address.toString))
      /*preloadNodesMap.remove(member.address)
      pe.getNodeMgr.removeNode(member.address)
      pe.getNodeMgr.removeStableNode(member.address)*/

    case _: MemberEvent => // ignore
  }
}