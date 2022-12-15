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

import akka.actor.{Address, Props}
import akka.cluster.ClusterEvent._
import akka.cluster.{Cluster, Member, MemberStatus}
import rep.network.cluster.MemberListener.Recollection
import rep.network.module.cfrd.CFRDActorType
import rep.utils.GlobalUtils.EventType
import rep.utils.TimeUtils
import org.slf4j.LoggerFactory
import rep.app.management.{ReasonOfStop, RepChainMgr}

import scala.collection.mutable.HashMap
import rep.network.base.ModuleBase
import rep.network.sync.SyncMsg.StartSync

import scala.collection.mutable.ArrayBuffer
import rep.log.RepLogger
import rep.network.util.NodeHelp
import rep.log.httplog.AlertInfo
import rep.network.autotransaction.Topic
import rep.network.consensus.byzantium.ConsensusCondition
import rep.proto.rc2.Event

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

  case object CollectionMemeberStatus

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

  val consensusCondition = new ConsensusCondition(pe.getRepChainContext)

  private var isStartSynch = false

  override def preStart(): Unit =
    super.preStart()

  cluster.subscribe(self, classOf[MemberEvent])
  cluster.subscribe(self, classOf[ClusterDomainEvent])
  //context.system.eventStream.subscribe(self, classOf[akka.remote.DisassociatedEvent])

  def memberRemovedHandler(member: Member): Unit = {
    RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix("Member is Removed: {}. {} nodes cluster" + "~" + member.address))
    System.err.println(s"MemberRemoved:printer=${pe.getSysTag} ~~ removed=${pe.getRepChainContext.getNodeMgr.getNodeName4AddrString(member.address.toString)}")

    val tmp = pe.getRepChainContext.getNodeMgr.getNodeName4AddrString(member.address.toString)
    if (tmp.equals(pe.getSysTag)) {
      RepChainMgr.ReStart(pe.getSysTag)
    }

    preloadNodesMap.remove(member.address)
    pe.getRepChainContext.getNodeMgr.removeNode(member.address)
    pe.getRepChainContext.getNodeMgr.removeStableNode(member.address)
    sendEvent(EventType.PUBLISH_INFO, mediator, NodeHelp.getNodeNameFromRoles(member.roles), Topic.Event, Event.Action.MEMBER_DOWN)
  }

  def memberLeaveHandler(member: Member): Unit = {
    RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix("Member is Removed: {}. {} nodes cluster" + "~" + member.address))
    System.err.println(s"MemberRemoved:printer=${pe.getSysTag} ~~ removed=${pe.getRepChainContext.getNodeMgr.getNodeName4AddrString(member.address.toString)}")

    val tmp = pe.getRepChainContext.getNodeMgr.getNodeName4AddrString(member.address.toString)
    if (tmp.equals(pe.getSysTag) && pe.getRepChainContext.getSignTool.getTrustCertificate(tmp) == null) {
      preloadNodesMap.remove(member.address)
      pe.getRepChainContext.getNodeMgr.removeNode(member.address)
      pe.getRepChainContext.getNodeMgr.removeStableNode(member.address)
      sendEvent(EventType.PUBLISH_INFO, mediator, NodeHelp.getNodeNameFromRoles(member.roles), Topic.Event, Event.Action.MEMBER_DOWN)
      val result = RepChainMgr.shutdown(pe.getSysTag, ReasonOfStop.Manual)
      RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}发起shutdown命令（因为信任证书被删除），启动结果=${result}"))
    }
  }

  /**
   * 节点状态是否稳定
   *
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
    case voteListChange:Array[String] =>
      val state = cluster.state
      state.members.foreach(m =>{
        val nn = NodeHelp.getNodeNameFromRoles(m.roles)
        if(!voteListChange.contains(nn) && m.status == MemberStatus.Up){
          pe.getRepChainContext.getNodeMgr.removeStableNode(m.address)
          RepLogger.trace(RepLogger.System_Logger, this.getLogMsgPrefix(s"remove not vote node,name=${nn}"))
          sendEvent(EventType.PUBLISH_INFO, mediator, nn, Topic.Event, Event.Action.MEMBER_DOWN)
        }else if(voteListChange.contains(nn) && m.status == MemberStatus.Up){
          pe.getRepChainContext.getNodeMgr.putStableNode(m.address, nn)
          sendEvent(EventType.PUBLISH_INFO, mediator, nn, Topic.Event, Event.Action.MEMBER_UP)
          RepLogger.trace(RepLogger.System_Logger, this.getLogMsgPrefix(s"add vote node,name=${nn}"))
        }else{
          RepLogger.trace(RepLogger.System_Logger, this.getLogMsgPrefix(s"print vote node,name=${nn},status=${m.status}"))
        }
      })

    //系统初始化时状态
    case state: CurrentClusterState =>
      RepLogger.trace(RepLogger.System_Logger, this.getLogMsgPrefix("Member call first time"))
      var nodes = Set.empty[Address]
      var snodes = new ArrayBuffer[(Address, String)]()
      state.members.foreach(m => {
        if (m.status == MemberStatus.Up) {
          nodes += m.address
          if (NodeHelp.isCandidateNow(NodeHelp.getNodeNameFromRoles(m.roles),pe.getRepChainContext.getConsensusNodeConfig.getVoteListOfConfig.toSet)) {
            snodes.append((m.address, NodeHelp.getNodeNameFromRoles(m.roles)))
            RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix(s"CurrentClusterState: nodes is candidator,node name =${NodeHelp.getNodeNameFromRoles(m.roles)}"))
            sendEvent(EventType.PUBLISH_INFO, mediator, NodeHelp.getNodeNameFromRoles(m.roles), Topic.Event, Event.Action.MEMBER_UP)
          } else {
            RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix(s"CurrentClusterState: nodes is candidator,node name =${m.address.toString}"))
          }
          pe.getRepChainContext.registerNode(NodeHelp.getNodeNameFromRoles(m.roles), m.address)
        }
        System.err.println(m.address.toString + "\t" + m.status.toString())
      })
      pe.getRepChainContext.getNodeMgr.resetNodes(nodes)
      pe.getRepChainContext.getNodeMgr.resetStableNodes(snodes.toSet)
      if (this.consensusCondition.CheckWorkConditionOfSystem(pe.getRepChainContext.getNodeMgr.getStableNodes.size)) {
        schedulerLink = scheduler.scheduleOnce((
          pe.getRepChainContext.getTimePolicy.getStableTimeDur).millis, self, Recollection)
      }

    //成员入网
    case MemberUp(member) =>
      RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix("Member is Up: {}. {} nodes in cluster" + "~" + member.address + "~" + pe.getRepChainContext.getNodeMgr.getNodes.mkString("|")))
      pe.getRepChainContext.getNodeMgr.putNode(member.address)
      if (member.roles != null && !member.roles.isEmpty && NodeHelp.isCandidateNow(NodeHelp.getNodeNameFromRoles(member.roles),pe.getRepChainContext.getConsensusNodeConfig.getVoteListOfConfig.toSet)) {
        RepLogger.sendAlertToDB(pe.getRepChainContext.getHttpLogger(), new AlertInfo("NETWORK", 4, s"Node Name=${NodeHelp.getNodeNameFromRoles(member.roles)},Node Address=${member.address.toString},is up."))
        preloadNodesMap.put(member.address, (TimeUtils.getCurrentTime(), NodeHelp.getNodeNameFromRoles(member.roles)))
        RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix(s"Member is Up:  nodes is condidator,node name=${NodeHelp.getNodeNameFromRoles(member.roles)}"))
        sendEvent(EventType.PUBLISH_INFO, mediator, NodeHelp.getNodeNameFromRoles(member.roles), Topic.Event, Event.Action.MEMBER_UP)
      } else {
        RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix(s"Member is Up:  nodes is not condidator,node address=${member.address.toString}"))
      }
      pe.getRepChainContext.registerNode(NodeHelp.getNodeNameFromRoles(member.roles), member.address)
      schedulerLink = scheduler.scheduleOnce((
        pe.getRepChainContext.getTimePolicy.getStableTimeDur).millis, self, Recollection)
    //稳定节点收集
    case Recollection =>
      schedulerLink = clearSched()
      RepLogger.trace(RepLogger.System_Logger, this.getLogMsgPrefix(" MemberListening recollection"))
      preloadNodesMap.foreach(node => {
        if (isStableNode(node._2._1, pe.getRepChainContext.getTimePolicy.getSysNodeStableDelay)) {
          RepLogger.sendAlertToDB(pe.getRepChainContext.getHttpLogger(), new AlertInfo("NETWORK", 4, s"Node Name=${node._2._2},Node Address=${node._2._1},is stable node."))
          pe.getRepChainContext.getNodeMgr.putStableNode(node._1, node._2._2)
        } else {
          RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix(s"Recollection:  nodes not stable,node name=${node._2._2}"))
        }
      })

      if (preloadNodesMap.size > 0) {
        pe.getRepChainContext.getNodeMgr.getStableNodes.foreach(node => {
          if (preloadNodesMap.contains(node)) {
            preloadNodesMap.remove(node)
            RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix(s"Recollection: clear preloadnodemap,node=${node}"))
          }
        })
      }

      if (!this.isStartSynch) {
        if (this.consensusCondition.CheckWorkConditionOfSystem(pe.getRepChainContext.getNodeMgr.getStableNodes.size)) {
          //组网成功之后开始系统同步
          RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix(s"Recollection:  system startup ,start sync,node name=${pe.getSysTag}"))
          pe.getActorRef(CFRDActorType.ActorType.synchrequester) ! StartSync(true)
          this.isStartSynch = true
        } else {
          RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix(s"Recollection:  nodes less ${pe.getRepChainContext.getConfig.getMinVoteNumber},node name=${pe.getSysTag}"))
        }
      } else {
        RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix(s"Recollection:  local consensus start finish,node name=${pe.getSysTag}"))
      }

      if (preloadNodesMap.size > 0) {
        //self ! Recollection
        schedulerLink = scheduler.scheduleOnce((
          pe.getRepChainContext.getTimePolicy.getStableTimeDur / 5).millis, self, Recollection)
      }

    //成员离网
    case MemberRemoved(member, _) =>
      /*RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix("Member is Removed: {}. {} nodes cluster" + "~" + member.address))

      System.err.println(s"MemberRemoved:printer=${pe.getSysTag} ~~ removed=${pe.getNodeMgr.getNodeName4AddrString(member.address.toString)}")

      val tmp = pe.getNodeMgr.getNodeName4AddrString(member.address.toString)
      if(tmp.equals(pe.getSysTag)){
        RepChainMgr.ReStart(pe.getSysTag)
      }

      preloadNodesMap.remove(member.address)
      pe.getNodeMgr.removeNode(member.address)
      pe.getNodeMgr.removeStableNode(member.address)
      sendEvent(EventType.PUBLISH_INFO, mediator, NodeHelp.getNodeName(member.roles), Topic.Event, Event.Action.MEMBER_DOWN)*/

      memberRemovedHandler(member)

    /*case event: akka.remote.DisassociatedEvent => //ignore
      RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix("DisassociatedEvent: {}. {} nodes cluster" + "~" + event.remoteAddress.toString))
      preloadNodesMap.remove(event.remoteAddress)
      pe.getNodeMgr.removeNode(event.remoteAddress)
      pe.getNodeMgr.removeStableNode(event.remoteAddress)*/
    case UnreachableMember(member) =>
      RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix("UnreachableMember is : {}. {} nodes cluster" + "~" + member.address))
      System.err.println(s"UnreachableMember:printer=${pe.getSysTag} ~~ removed=${pe.getRepChainContext.getNodeMgr.getNodeName4AddrString(member.address.toString)}")
      preloadNodesMap.remove(member.address)
      pe.getRepChainContext.getNodeMgr.removeNode(member.address)
      pe.getRepChainContext.getNodeMgr.removeStableNode(member.address)
      sendEvent(EventType.PUBLISH_INFO, mediator, NodeHelp.getNodeNameFromRoles(member.roles), Topic.Event, Event.Action.MEMBER_DOWN)
    case ReachableMember(member) =>
      RepLogger.trace(RepLogger.System_Logger, this.getLogMsgPrefix(" ReachableMember recollection"))
      val addr = member.address
      if (member.status == MemberStatus.up) {
        if (member.roles != null && !member.roles.isEmpty && NodeHelp.isCandidateNow(NodeHelp.getNodeNameFromRoles(member.roles),pe.getRepChainContext.getConsensusNodeConfig.getVoteListOfConfig.toSet)) {
          val name = NodeHelp.getNodeNameFromRoles(member.roles)
          pe.getRepChainContext.getNodeMgr.putNode(addr)
          pe.getRepChainContext.getNodeMgr.putStableNode(addr, name)
          sendEvent(EventType.PUBLISH_INFO, mediator, name, Topic.Event, Event.Action.MEMBER_UP)
        } else {
          RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix(s"ReachableMember is Up:  nodes is not condidator,node address=${member.address.toString}"))
        }
      } else {
        RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix(s"ReachableMember is not Up:  node address=${member.address.toString}"))
      }


      if (!this.isStartSynch) {
        if (this.consensusCondition.CheckWorkConditionOfSystem(pe.getRepChainContext.getNodeMgr.getStableNodes.size)) {
          //组网成功之后开始系统同步
          RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix(s"ReachableMember Recollection:  system startup ,start sync,node name=${pe.getSysTag}"))
          pe.getActorRef(CFRDActorType.ActorType.synchrequester) ! StartSync(true)
          this.isStartSynch = true
        } else {
          RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix(s"ReachableMember Recollection:  nodes less ${pe.getRepChainContext.getConfig.getMinVoteNumber},node name=${pe.getSysTag}"))
        }
      } else {
        RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix(s"ReachableMember Recollection:  local consensus start finish,node name=${pe.getSysTag}"))
      }

    case MemberLeft(member) => //ignore
      System.err.println("MemberLeft:" + member.address.toString + "\t" + member.status.toString())
      RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix("MemberLeft: {}. {} nodes cluster" + "~" + member.address.toString))
    /*preloadNodesMap.remove(member.address)
    pe.getNodeMgr.removeNode(member.address)
    pe.getNodeMgr.removeStableNode(member.address)*/
    //memberRemovedHandler(member)

    case MemberExited(member) => //ignore
      System.err.println("MemberExited:" + member.address.toString + "\t" + member.status.toString())
      RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix("MemberExited: {}. {} nodes cluster" + "~" + member.address.toString))
    /*preloadNodesMap.remove(member.address)
    pe.getNodeMgr.removeNode(member.address)
    pe.getNodeMgr.removeStableNode(member.address)*/
    //memberRemovedHandler(member)
    case MemberDowned(member) =>
      System.err.println("MemberDowned:" + member.address.toString + "\t" + member.status.toString())
    //memberRemovedHandler(member)
    case _: MemberEvent => // ignore
  }
}