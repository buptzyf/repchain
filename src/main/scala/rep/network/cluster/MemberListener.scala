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

import java.util

import akka.actor.{Actor, Address, Props}
import akka.cluster.ClusterEvent._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.{Cluster, MemberStatus}
import rep.app.conf.TimePolicy
import rep.app.conf.SystemProfile
import rep.network.cluster.MemberListener.{CheckUnreachableInfo, Recollection, unreachableNodeInfo}
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
import rep.log.httplog.AlertInfo
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
  case object CheckUnreachableInfo
  case class unreachableNodeInfo(raddr:Address,status:MemberStatus,nodeName:String,start:Long)
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

  //处理Unreachable情况，特别实在窄带，并且网络不稳定的情况下，使用一下代码
  var unreachableMembers = HashMap[Address,unreachableNodeInfo]()
  var schedulerOfUnReachable: akka.actor.Cancellable = null
  //建立unreachable事情的检查机制
  schedulerOfUnReachable  = context.system.scheduler.scheduleOnce(60.seconds,self,CheckUnreachableInfo)

  private var isStartSynch = false
  private var isRestart = false

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
      //this.unreachableMembers -= member.address.toString

      if (member.roles != null && !member.roles.isEmpty && NodeHelp.isCandidatorNode(member.roles)) {
        RepLogger.sendAlertToDB(new AlertInfo("NETWORK",4,s"Node Name=${NodeHelp.getNodeName(member.roles)},Node Address=${member.address.toString},is up."))
        preloadNodesMap.put(member.address, (TimeUtils.getCurrentTime(), NodeHelp.getNodeName(member.roles)))
        RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix(s"Member is Up:  nodes is condidator,node name=${NodeHelp.getNodeName(member.roles)}"))
        sendEvent(EventType.PUBLISH_INFO, mediator, NodeHelp.getNodeName(member.roles), Topic.Event, Event.Action.MEMBER_UP)
      } else {
        RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix(s"Member is Up:  nodes is not condidator,node address=${member.address.toString}"))
      }

      this.unreachableMembers -= member.address
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
          RepLogger.sendAlertToDB(new AlertInfo("NETWORK",4,s"Node Name=${node._2._2},Node Address=${node._2._1},is stable node."))
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

    case CheckUnreachableInfo=>
      if(this.schedulerOfUnReachable != null){this.schedulerOfUnReachable.cancel()}
      //todo 编写unreachable处理过程
      if(!this.unreachableMembers.isEmpty){
        //存在unreachable节点
        RepLogger.info(RepLogger.System_Logger,s"exist unreachable node ~~ size=${this.unreachableMembers.size}")
        val mcount = cluster.state.members.size
        RepLogger.info(RepLogger.System_Logger,s"cluster node count ~~ size=${mcount}")
        if(mcount >= 4){
          if(this.unreachableMembers.size >= (mcount - 1) && (System.currentTimeMillis() - getLastUnreachableTime)/1000 >= TimePolicy.getNodeRestartForUnreachableTime){
            //本节点看到了所有其他节点不可达，那么问题出在自身节点的问题比较多，因此自动重启自身节点
            //重启方式沿用之前的启动方式
            if(!pe.getSysTag.equalsIgnoreCase(SystemProfile.getGenesisNodeName)){
              //如果节点不是创世节点（唯一种子节点）可以重启
              RepLogger.info(RepLogger.System_Logger,s"unreachable node is self,restart node ~~ sysName=${pe.getSysTag}")
              if(!this.isRestart){
                this.isRestart = true
                RepChainMgr.ReStart(pe.getSysTag)
                RepLogger.info(RepLogger.System_Logger,s"unreachable node is self,restart command finish ~~ sysName=${pe.getSysTag}")
              }
            }
          }else{
            //本节点跟大多数节点都保持联系，只有小数节点出现网络故障，那么对于超时链接不上的节点，发出down的消息
            //发出down的消息可以是种子节点，也可以是领导节点
            RepLogger.info(RepLogger.System_Logger,s"unreachable node is not self ")
            var rls = new util.ArrayList[Address]()
            if(pe.getSysTag.equalsIgnoreCase(SystemProfile.getGenesisNodeName) || cluster.selfAddress.toString.equals(cluster.state.leader.get.toString) ){
              this.unreachableMembers.foreach(node=>{
                if((System.currentTimeMillis() - node._2.start)/1000 >= TimePolicy.getNodeRestartForUnreachableTime){
                  RepLogger.info(RepLogger.System_Logger,s"unreachable node is not self,down node=${node._1} ~~ sysName=${pe.getSysTag}")
                  cluster.down(node._1)
                  rls.add(node._1)
                  RepLogger.info(RepLogger.System_Logger,s"unreachable node is not self,down node finish=${node._1}  ~~ sysName=${pe.getSysTag}")
                }
              })
            }
            //已经down的节点，启动删除，不再做维护工作
            rls.forEach(f=>{
              this.unreachableMembers.remove(f)
            })
          }
        }else{
          //节点数量小于4，说明系统组网没有完成就出现问题，不要自动处理，需要人工干预
        }
      }
      schedulerOfUnReachable  = context.system.scheduler.scheduleOnce(60.seconds,self,CheckUnreachableInfo)
    //成员离网
    case MemberRemoved(member, _) =>
      RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix("Member is Removed: {}. {} nodes cluster" + "~" + member.address))

      System.err.println(s"MemberRemoved:printer=${pe.getSysTag} ~~ removed=${pe.getNodeMgr.getNodeName4AddrString(member.address.toString)}")

      val tmp = pe.getNodeMgr.getNodeName4AddrString(member.address.toString)
      //this.unreachableMembers -= member.address.toString

      if(tmp.equals(pe.getSysTag)){
        //RepChainMgr.ReStart(pe.getSysTag)
        RepLogger.sendAlertToDB(new AlertInfo("NETWORK",2,s"Node Name=${pe.getSysTag},Node Address=${member.address.toString},is remove."))
        System.err.println(s"MemberRemoved,self remove:printer=${pe.getSysTag} ~~ removed=${pe.getNodeMgr.getNodeName4AddrString(member.address.toString)}")
        if(!this.isRestart){
          System.err.println(s"MemberRemoved,remove restart:printer=${pe.getSysTag} ~~ removed=${pe.getNodeMgr.getNodeName4AddrString(member.address.toString)}")
          this.isRestart = true
          RepChainMgr.ReStart(pe.getSysTag)
        }
      }

      this.unreachableMembers -= member.address

      preloadNodesMap.remove(member.address)
      pe.getNodeMgr.removeNode(member.address)
      pe.getNodeMgr.removeStableNode(member.address)
      sendEvent(EventType.PUBLISH_INFO, mediator, NodeHelp.getNodeName(member.roles), Topic.Event, Event.Action.MEMBER_DOWN)

    case UnreachableMember(member)=>
      RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix("UnreachableMember is : {}. {} nodes cluster" + "~" + member.address))
      System.err.println(s"UnreachableMember:printer=${pe.getSysTag} ~~ removed=${pe.getNodeMgr.getNodeName4AddrString(member.address.toString)}")
      preloadNodesMap.remove(member.address)
      pe.getNodeMgr.removeNode(member.address)
      pe.getNodeMgr.removeStableNode(member.address)
      this.unreachableMembers += member.address -> unreachableNodeInfo(member.address,member.status,NodeHelp.getNodeName(member.roles),System.currentTimeMillis())
      sendEvent(EventType.PUBLISH_INFO, mediator, NodeHelp.getNodeName(member.roles), Topic.Event, Event.Action.MEMBER_DOWN)

    /*RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix("UnreachableMember is : {}. {} nodes cluster" + "~" + member.address))
      System.err.println(s"UnreachableMember:printer=${pe.getSysTag} ~~ removed=${pe.getNodeMgr.getNodeName4AddrString(member.address.toString)}")

      val tmp = pe.getNodeMgr.getNodeName4AddrString(member.address.toString)

      if(tmp != ""){
        if(!this.unreachableMembers.contains(member.address.toString)){
          this.unreachableMembers += member.address.toString->System.currentTimeMillis()
        }

        preloadNodesMap.remove(member.address)
        pe.getNodeMgr.removeNode(member.address)
        pe.getNodeMgr.removeStableNode(member.address)
        sendEvent(EventType.PUBLISH_INFO, mediator, NodeHelp.getNodeName(member.roles), Topic.Event, Event.Action.MEMBER_DOWN)
      }

      if(pe.getNodeMgr.getStableNodeNames.size == 1 && this.unreachableMembers.size >= 3 && //this.isUnreachableTimeout &&
                                            this.unreachableMembers.size >= (SystemProfile.getVoteNodeList.size()/2 + 1) &&
                                                                                            SystemProfile.getGenesisNodeName != pe.getSysTag){
        //所有其他节点都是不可达节点，不是创世节点，可以重启
        if(!this.isRestart){
          System.err.println(s"UnreachableMember,unreachable restart:printer=${pe.getSysTag}")
          this.isRestart = true
          RepChainMgr.ReStart(pe.getSysTag)
        }
      }else{
        if( SystemProfile.getGenesisNodeName != pe.getSysTag && RepChainMgr.isChangeIpAddress(pe.getSysTag)){
          RepLogger.sendAlertToDB(new AlertInfo("NETWORK",2,s"Node Name=${pe.getSysTag},IP has Changed."))
          System.err.println(s"UnreachableMember,ipChange:printer=${pe.getSysTag} ")
          if(!this.isRestart){
            System.err.println(s"UnreachableMember,unreachable restart:printer=${pe.getSysTag}")
            this.isRestart = true
            RepChainMgr.ReStart(pe.getSysTag)
          }
        }
      }*/

    case ReachableMember(member) =>
      System.err.println(" ReachableMember recollection")
      val addr = member.address
      this.unreachableMembers -= member.address
      if(member.status == MemberStatus.up){
        if(member.roles != null && !member.roles.isEmpty && NodeHelp.isCandidatorNode(member.roles)){
          val name = NodeHelp.getNodeName(member.roles)
          pe.getNodeMgr.putNode(addr)
          pe.getNodeMgr.putStableNode(addr, name)
          sendEvent(EventType.PUBLISH_INFO, mediator, name, Topic.Event, Event.Action.MEMBER_UP)
        }else{
          System.err.println(s"ReachableMember is Up:  nodes is not condidator,node address=${member.address.toString}")
        }
        System.err.println(s"ReachableMember is Up:  node address=${member.address.toString}")
      }else{
        System.err.println(s"ReachableMember is not Up:  node address=${member.address.toString}")
      }


      if (!this.isStartSynch) {
        if (ConsensusCondition.CheckWorkConditionOfSystem(pe.getNodeMgr.getStableNodes.size)) {
          //组网成功之后开始系统同步
          System.err.println(s"ReachableMember Recollection:  system startup ,start sync,node name=${pe.getSysTag}")
          pe.getActorRef(CFRDActorType.ActorType.synchrequester) ! StartSync(true)
          this.isStartSynch = true
        } else {
          System.err.println(s"ReachableMember Recollection:  nodes less ${SystemProfile.getVoteNodeMin},node name=${pe.getSysTag}")
        }
      } else {
        System.err.println(s"ReachableMember Recollection:  local consensus start finish,node name=${pe.getSysTag}")
      }

    case MemberLeft(member) => //ignore
      RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix("MemberLeft: {}. {} nodes cluster" + "~" + member.address.toString))
      this.unreachableMembers -= member.address
    /*preloadNodesMap.remove(member.address)
      pe.getNodeMgr.removeNode(member.address)
      pe.getNodeMgr.removeStableNode(member.address)*/

    case MemberExited(member) => //ignore
      RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix("MemberExited: {}. {} nodes cluster" + "~" + member.address.toString))
      this.unreachableMembers -= member.address
    /*preloadNodesMap.remove(member.address)
      pe.getNodeMgr.removeNode(member.address)
      pe.getNodeMgr.removeStableNode(member.address)*/

    case _: MemberEvent => // ignore
  }

  def  getLastUnreachableTime:Long={
    var ct = System.currentTimeMillis()
    this.unreachableMembers.foreach(v=>{
      if(v._2.start < ct){
        ct = v._2.start
      }
    })
    ct
  }

  /*def isUnreachableTimeout:Boolean={
    var b = true
    val timeout = 10000
    val start = System.currentTimeMillis()
    breakable(
    this.unreachableMembers.keySet.foreach(key =>{
      if((start - this.unreachableMembers(key)) < timeout ){
        b = false
        break
      }
    })
    )
    b
  }*/
}