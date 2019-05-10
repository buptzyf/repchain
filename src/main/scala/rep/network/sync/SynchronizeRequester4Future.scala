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

package rep.network.sync

import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import akka.pattern.AskTimeoutException
import scala.concurrent._

import akka.actor.{ ActorRef, Props, Address, ActorSelection }
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import rep.network.base.ModuleBase
import rep.app.conf.TimePolicy
import rep.network.module.ModuleManager
import rep.storage.ImpDataAccess
import rep.protos.peer._
import rep.network.persistence.Storager.{ BlockRestore, SourceOfBlock }
import scala.collection._
import rep.utils.GlobalUtils.{ ActorType, BlockEvent, EventType, NodeStatus }
import rep.app.conf.SystemProfile
import rep.network.util.NodeHelp
import rep.network.sync.SyncMsg.{ ResponseInfo, StartSync,  BlockDataOfRequest, BlockDataOfResponse, SyncRequestOfStorager,ChainInfoOfRequest }
import scala.util.control.Breaks._
import rep.log.RepLogger
import scala.collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer

object SynchronizeRequester4Future {
  def props(name: String): Props = Props(classOf[SynchronizeRequester4Future], name)

}

class SynchronizeRequester4Future(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.concurrent.duration._

  implicit val timeout = Timeout(TimePolicy.getTimeoutSync seconds)
  private val responseActorName = "/user/modulemanager/synchresponser"

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("SynchronizeRequester4Future Start"))
  }

  private def toAkkaUrl(addr: Address, actorName: String): String = {
    return addr.toString + "/" + actorName;
  }

  private def AsyncGetNodeOfChainInfo(addr: Address,lh:Long): Future[ResponseInfo] = Future {
    //println(s"${pe.getSysTag}:entry AsyncGetNodeOfChainInfo")
    var result: ResponseInfo = null

    try {
      val selection: ActorSelection = context.actorSelection(toAkkaUrl(addr, responseActorName));
      val future1 = selection ? ChainInfoOfRequest(lh)
      //logMsg(LogType.INFO, "--------AsyncGetNodeOfChainInfo success")
      result = Await.result(future1, timeout.duration).asInstanceOf[ResponseInfo]
    } catch {
      case e: AskTimeoutException =>
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------AsyncGetNodeOfChainInfo timeout"))
        null
      case te: TimeoutException =>
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------AsyncGetNodeOfChainInfo java timeout"))
        null
    }

    RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"entry AsyncGetNodeOfChainInfo after,chaininfo=${result}"))
    result
  }

  private def AsyncGetNodeOfChainInfos(stablenodes: Set[Address],lh:Long): List[ResponseInfo] = {
    //println(s"${pe.getSysTag}:entry AsyncGetNodeOfChainInfos")
    //var result = new immutable.TreeMap[String, ResponseInfo]()
    val listOfFuture: Seq[Future[ResponseInfo]] = stablenodes.toSeq.map(addr => {
      AsyncGetNodeOfChainInfo(addr,lh)
    })

    val futureOfList: Future[List[ResponseInfo]] = Future.sequence(listOfFuture.toList).recover({
      case e: Exception =>
        null
    })
    RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry AsyncGetNodeOfChainInfos 1"))
    try {
      val result1 = Await.result(futureOfList, timeout.duration).asInstanceOf[List[ResponseInfo]]
      RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry AsyncGetNodeOfChainInfos 2"))
      if (result1 == null) {
        List.empty
      } else {
        result1
      }
    } catch {
      case te: TimeoutException =>
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------AsyncGetNodeOfChainInfo java timeout"))
        null
    }
  }

  /*private def getSyncInfo(infos: List[ResponseInfo], nodelength: Int, localHeight: Long): GreatMajority = {
    var majority: GreatMajority = null
    if (infos != null) {
      var nodecount = 0
      var nodeheight = 0l
      var addr: ActorRef = null
      var oneAddr: ActorRef = null

      infos.foreach(f => {
        if (f != null) {
          if (f.response.height > localHeight) {
            nodecount += 1
            if (nodeheight == 0) {
              nodeheight = f.response.height
              addr = f.responser
            } else {
              if (f.response.height <= nodeheight) {
                nodeheight = f.response.height
                addr = f.responser
              }
            }
          }
          if (f.response.height == 1) {
            oneAddr = f.responser
          }
        }
      })

      if (NodeHelp.ConsensusConditionChecked(nodecount, nodelength)) {
        if (nodeheight == 0 && localHeight == 0 && oneAddr != null) {
          majority = GreatMajority(oneAddr, 1)
        } else if (nodeheight > 0) {
          majority = GreatMajority(addr, nodeheight)
        }
      } else {
        if (nodeheight == 1 && localHeight == 0 && oneAddr != null) {
          majority = GreatMajority(oneAddr, 1)
        }
      }
    }
    majority
  }*/

  
  private def getBlockData(height: Long, ref: ActorRef): Boolean = {
    try {
      
      val future1 = ref ? BlockDataOfRequest(height)
      //logMsg(LogType.INFO, "--------AsyncGetNodeOfChainInfo success")
      var result = Await.result(future1, timeout.duration).asInstanceOf[BlockDataOfResponse]
      RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"height=${height}--------getBlockData success"))
      pe.getActorRef(ActorType.storager) ! BlockRestore(result.data, SourceOfBlock.SYNC_BLOCK, ref)
      true
    } catch {
      case e: AskTimeoutException =>
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------getBlockData timeout"))
        false
      case te: TimeoutException =>
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------getBlockData java timeout"))
        false
    }
  }

  private def getBlockDatas(lh:Long,rh:Long,actorref:ActorRef) = {
    if (rh > lh) {
      var height = lh + 1
      while (height <= rh) {
        if (!pe.getBlockCacheMgr.exist(height)) {
          if (!getBlockData(height, actorref)) {
            getBlockData(height, actorref)
          }
        }
        height += 1
      }
    }
  }

  
  private def checkHashAgreement(h:Long,ls:List[ResponseInfo],ns:Int,checkType:Int):(Boolean,String)={
    val hls = ls.filter(_.response.height == h)
    var gls : List[(String, Int)] = null
    checkType match{
      case 1 =>
        //检查远端的最后一个块的hash的一致性
          gls = hls.groupBy(x => x.response.currentBlockHash.toStringUtf8()).map(x => (x._1,x._2.length)).toList.sortBy(x => -x._2)
      case 2 =>
        //检查远端指定高度块的一致性
        gls = hls.groupBy(x => x.ChainInfoOfSpecifiedHeight.currentBlockHash.toStringUtf8()).map(x => (x._1,x._2.length)).toList.sortBy(x => -x._2)
    }
    val tmpgHash = gls.head._1
    val tmpgCount = gls.head._2
    if(NodeHelp.ConsensusConditionChecked(tmpgCount, ns)){
      (true,tmpgHash)
    }else{
      (false,"")
    }
  }
 
  
 
  private def Handler = {
    val lh = pe.getCurrentHeight
    val lhash = pe.getCurrentBlockHash
    val lprehash = pe.getSystemCurrentChainStatus.previousBlockHash.toStringUtf8()
    val nodes = pe.getNodeMgr.getStableNodes
    val res = AsyncGetNodeOfChainInfos(nodes,lh)
    
    if(NodeHelp.ConsensusConditionChecked(res.length + 1, nodes.size)){
      //获取到到chaininfo信息的数量，得到大多数节点的响应，进一步判断同步的策略
      //获取返回的chaininfo信息中，大多数节点的相同高度的最大值
       val heightStatis = res.groupBy(x => x.response.height).map(x => (x._1,x._2.length)).toList.sortBy(x => -x._2)
       val maxheight = heightStatis.head._1
       var nodesOfmaxHeight = heightStatis.head._2
       
       if(NodeHelp.ConsensusConditionChecked(nodesOfmaxHeight, nodes.size)){
         //得到了真正大多数节点相同的高度
         val agreementResult = checkHashAgreement(maxheight,res,nodes.size,1)
         if(agreementResult._1){
           //当前同步高度的最后高度的块hash一致
           if(maxheight > lh){
             //待同步高度大于本地高度
             if(lh == 0){
               //当前本地高度0，没有前序块，不需要检查前序块hash，可以直接进入同步
               val actorref = res.filter(_.response.height == maxheight).head.responser
               getBlockDatas(lh,maxheight,actorref)
               RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------error,大于本地高度，同步完成"))
             }else{
               //本地高度大于0，需要检查前序块的hash是否一致
               val leadingAgreementResult = checkHashAgreement(maxheight,res,nodes.size,2)
               if(leadingAgreementResult._1){
                 //远端前序块hash检查一致
                 if(leadingAgreementResult._2 == lhash){
                   //本地最后块的hash与远端的前序块hash一致，可以直接进入同步
                   val actorref = res.filter(_.response.height == maxheight).
                                       filter(_.response.currentBlockHash.toStringUtf8() == agreementResult._2).
                                       filter(_.ChainInfoOfSpecifiedHeight.currentBlockHash.toStringUtf8() == leadingAgreementResult._2).head.responser
                   getBlockDatas(lh,maxheight,actorref)
                   RepLogger.info(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------error,大于本地高度，同步完成"))
                 }else{
                   //由于与远端的前序块的hash不一致，需要进一步进入回滚当前块的判断
                   val fls = res.filter(_.response.height == maxheight).
                                 filter(_.ChainInfoOfSpecifiedHeight.currentBlockHash.toStringUtf8() == leadingAgreementResult._2)
                   if(fls.nonEmpty){
                     val chinfo = fls.head
                     if(lprehash == chinfo.ChainInfoOfSpecifiedHeight.previousBlockHash.toStringUtf8()){
                       //本地块的前一块的hash与远端前导块的前一块的hash一致，可以调用块回滚到前一个块，直接调用存储持久化代码，进行块回滚，回滚之后载同步
                       val actorref = res.filter(_.response.height == maxheight).
                                     filter(_.response.currentBlockHash.toStringUtf8() == agreementResult._2).
                                     filter(_.ChainInfoOfSpecifiedHeight.currentBlockHash.toStringUtf8() == leadingAgreementResult._2).head.responser
                       val da = ImpDataAccess.GetDataAccess(pe.getSysTag)
                       if(da.rollbackToheight(lh-1)){
                         pe.resetSystemCurrentChainStatus(da.getBlockChainInfo())
                         getBlockDatas(lh-1,maxheight,actorref)
                         RepLogger.info(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------error,大于本地高度，回滚成功"))
                       }else{
                         RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------error,大于本地高度，回滚失败"))
                       }
                     }else{
                       //本地块的前一块的hash与远端前导块的前一块的hash不一致，输出错误，停止同步
                       RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------error,大于本地高度，本地块的前一块的hash与远端前导块的前一块的hash不一致，输出错误，停止同步"))
                     }
                   }else{
                     //找不到正确的响应信息，输出错误，停止同步
                     RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------error,大于本地高度，找不到正确的响应信息，输出错误，停止同步"))
                   }
                 }
               }else{
                 //远端前序块hash不一致，输出错误，停止同步
                 RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------error,大于本地高度，远端前序块hash不一致，输出错误，停止同步"))
               }
             }
           }else if(maxheight == lh ){
             //待同步高度等于本地高度
             if(lh == 0){
               //当前本地高度0，又与大多数节点相同，说明系统处于初始状态，寻找种子节点是否已经建立创世块
               val resinfo = res.filter(_.response.height == 1)
               if(resinfo.nonEmpty){
                   var actorref : ActorRef = null
                   breakable(
                   resinfo.foreach(f=>{
                     if(NodeHelp.isSeedNode(pe.getNodeMgr.getStableNodeName4Addr(f.responser.path.address))){
                       actorref = f.responser
                       break
                     }
                     })
                     )
                   if(actorref != null){
                     //找到创世节点，启动同步创世块
                     getBlockDatas(lh,1,actorref)
                     RepLogger.info(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------error,等于本地高度，找到创世节点，启动同步创世块"))
                   }else{
                     //没有找到创世节点，输出信息，停止同步
                     RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------error,等于本地高度，没有找到创世节点，输出信息，停止同步"))
                   }
                 }else{
                   ////没有找到创世节点，输出信息，停止同步
                   RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------error,等于本地高度，//没有找到创世节点，输出信息，停止同步"))
                 }
               }else{
                 //系统非初始状态，检查hash一致性
                 val agreementResult = checkHashAgreement(maxheight,res,nodes.size,1)
                 if(!agreementResult._1){
                   //当前最后高度的块hash不一致，输出错误，停止同步
                    RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------error,等于本地高度，当前最后高度的块hash不一致，输出错误，停止同步"))
                 }else{
                   //当前最后高度的块hash一致，检查自己是否与他们相等
                   if(lhash == agreementResult._2){
                     //自己同大多数节点hash一致，完成同步
                     RepLogger.info(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------error,等于本地高度，自己同大多数节点hash一致，完成同步"))
                   }else{
                     //开始检查回滚
                     val tmpres = res.filter(_.response.height == maxheight).
                                     filter(_.response.currentBlockHash.toStringUtf8() == agreementResult._2).head
                     if(lprehash == tmpres.response.previousBlockHash.toStringUtf8()){
                       //可以开始回滚
                       val da = ImpDataAccess.GetDataAccess(pe.getSysTag)
                       if(da.rollbackToheight(lh-1)){
                         pe.resetSystemCurrentChainStatus(da.getBlockChainInfo())
                         getBlockDatas(lh-1,lh,tmpres.responser)
                         RepLogger.info(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------error,等于本地高度，回滚完成，完成同步"))
                       }else{
                         RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------error,等于本地高度，回滚失败，停止同步"))
                       }
                     }else{
                       //前一个块的hash不一致，输出错误，停止同步
                       RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------error,等于本地高度，前一个块的hash不一致，输出错误，停止同步"))
                     }
                   }
                 }
               }
           }else{
             //待同步高度小于本地高度
             if(lh == 1){
               if(NodeHelp.isSeedNode(pe.getSysTag)){
                 //当前系统是种子节点，并且是创世块，完成同步
                 RepLogger.info(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------error,小于本地高度，当前系统是种子节点，并且是创世块，完成同步"))
               }
             }else{
               if(maxheight > 0){
                 //检查其他节点是否一致
                 val agreementResult = checkHashAgreement(maxheight,res,nodes.size,1)
                 if(!agreementResult._1){
                   //当前最后高度的块hash不一致，输出错误，停止同步
                   RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------error,小于本地高度，当前最后高度的块hash不一致，输出错误，停止同步"))
                 }else{
                   //如果一致
                   val da = ImpDataAccess.GetDataAccess(pe.getSysTag)
                   val block = da.getBlockByHash(agreementResult._2)
                   if(block != null){
                     //需要回滚
                     if(da.rollbackToheight(maxheight)){
                         pe.resetSystemCurrentChainStatus(da.getBlockChainInfo())
                         //完成同步
                         RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------error,小于本地高度，完成同步"))
                      }else{
                        //回滚失败，输出错误，停止同步
                        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------error,小于本地高度，回滚失败，输出错误，停止同步"))
                      }
                   }
                 }
               }else{
                 //远程高度小于0，完成同步
                 RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------error,远程高度小于0，完成同步"))
               }
             }
           }
         }else{
           //当前同步高度的最后高度的块hash不一致，输出错误信息，停止同步
           RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------error,当前同步高度的最后高度的块hash不一致，输出错误信息，停止同步"))
         }
       }else{
         //最多数量的高度，达不到共识的要求，输出错误信息停止同步
         RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------error,最多数量的高度，达不到共识的要求，输出错误信息停止同步 response size=${res.size}"))
       }
    }else{
      //获取到到chaininfo信息的数量，没有得到大多数节点的响应，输出错误信息停止同步
      RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------error,获取到到chaininfo信息的数量，没有得到大多数节点的响应，输出错误信息停止同步 response size=${res.size}"))
    }
  }

  private def initSystemChainInfo = {
    if (pe.getCurrentHeight == 0) {
      val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
      pe.resetSystemCurrentChainStatus(dataaccess.getBlockChainInfo())
    }
  }

  override def receive: Receive = {
    case StartSync(isNoticeModuleMgr: Boolean) =>
      initSystemChainInfo
      if (pe.getNodeMgr.getStableNodes.size >= SystemProfile.getVoteNoteMin && !pe.isSynching) {
        pe.setSynching(true)
        Handler
        pe.setSynching(false)
        if (isNoticeModuleMgr)
          pe.getActorRef(ActorType.modulemanager) ! ModuleManager.startup_Consensus
      } else {
        RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"too few node,min=${SystemProfile.getVoteNoteMin} or synching  from actorAddr" + "～" + NodeHelp.getNodePath(sender())))
      }

    case SyncRequestOfStorager(responser, maxHeight) =>
      if (!pe.isSynching) {
        pe.setSynching(true)
        getBlockDatas(pe.getCurrentHeight,maxHeight,responser)
        pe.setSynching(false)
      }
  }
}
