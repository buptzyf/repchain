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

package rep.network.cache

import java.security.cert.Certificate

import com.google.protobuf.ByteString

import akka.actor.{Actor, Address, Props}
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import rep.app.conf.SystemProfile
import rep.crypto.cert.SignTool
import rep.network.{ PeerHelper, Topic }
import rep.network.base.ModuleBase
import rep.network.cache.TransactionPool.CheckedTransactionResult
import rep.network.consensus.vote.CRFDVoterModule.VoteRecover
import rep.protos.peer.ChaincodeId
import rep.protos.peer.{ Event, Transaction }
import rep.storage.IdxPrefix.WorldStateKeyPreFix
import rep.storage.ImpDataAccess
import rep.utils.{ ActorUtils, GlobalUtils }
import rep.utils.GlobalUtils.EventType
import rep.utils.SerializeUtils
import rep.log.trace.LogType

/**
 * 交易缓冲池伴生对象
 *
 * @author shidianyue
 * @version 1.0
 */
object TransactionPool {
  def props(name: String): Props = Props(classOf[TransactionPool], name)
  //交易检查结果
  case class CheckedTransactionResult(result: Boolean, msg: String)

}
/**
 * 交易缓冲池类
 *
 * @author shidianyue
 * @version 1.0
 * @param moduleName
 */

class TransactionPool(moduleName: String) extends ModuleBase(moduleName) {
  import akka.actor.ActorSelection 
  
  val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)

  override def preStart(): Unit = {
    //注册接收交易的广播
    SubscribeTopic(mediator, self, selfAddr, Topic.Transaction, true)
  }
  
   def toAkkaUrl(sn : Address, actorName:String):String = {  
        return sn.toString + "/"  + actorName;  
    }

      def visitStoreService(sn : Address, actorName:String,t1:Transaction) = {  
        try {  
          val  selection : ActorSelection  = context.actorSelection(toAkkaUrl(sn , actorName));  
          selection ! t1 
        } catch  {  
             case e: Exception => e.printStackTrace()
        }  
    }  
  
  /**
   * 检查交易是否符合规则
   * @param t
   * @param dataAccess
   * @return
   */
  def checkTransaction(t: Transaction, dataAccess: ImpDataAccess): CheckedTransactionResult = {
    var resultMsg = ""
    var result = false
    val sig = t.getSignature
    val tOutSig = t.clearSignature//t.withSignature(null)
    val cert = sig.getCertId
    
    try{
          val siginfo = sig.signature.toByteArray()
          
          SignTool.verify(siginfo, tOutSig.toByteArray, cert, pe.getSysTag) match {
            case true =>
                dataAccess.getBlockByTxId(t.id) match {
                  case null => result = true
                  case _    => resultMsg = s"The transaction(${t.id}) is duplicated with txid"
                }
              case false => resultMsg = s"The transaction(${t.id}) is not completed"
          }
        
      }catch{
        case e : RuntimeException => throw e
      }

    CheckedTransactionResult(result, resultMsg)
  }

  override def receive = {
    //处理接收的交易
    case t: Transaction =>
      //我们在这里并不缓存该Transaction，在接收到同级别的广播时再进行缓存
      
     // mediator ! Publish(Topic.Transaction, t)
        //pe.getStableNodes.foreach(sn=>{
        //       visitStoreService(sn , "/user/moduleManager/transactionPool",t)                      
        //})      
      
      if (ActorUtils.isHelper(sender().path.toString) ||
        ActorUtils.isAPI(sender().path.toString)) {
        //广播交易
        mediator ! Publish(Topic.Transaction, t)
        
        
        
        //广播发送交易事件
        sendEvent(EventType.PUBLISH_INFO, mediator, selfAddr, Topic.Transaction, Event.Action.TRANSACTION)
      } else {
        //交易缓存,这里默认各个节点的交易缓存都是一致的。但是因为入网时间不一致，所有可能会有区别。最终以出块人的交易为准
        //TODO kami 验证交易签名和证书
        //val checkedTransactionResult = CheckedTransactionResult(true, "")
        sendEvent(EventType.RECEIVE_INFO, mediator, selfAddr, Topic.Transaction, Event.Action.TRANSACTION)
        val checkedTransactionResult = checkTransaction(t, dataaccess)
        checkedTransactionResult.result match {
          case false => //ignore
          case true =>
            //签名验证成功
            if(pe.getTransPoolMgr.getTransLength() < 100)
              logMsg(LogType.INFO,s"<<<<<<<<<<<<<>>>>>>>>>transaction=${pe.getTransPoolMgr.getTransLength()}" )
            if (SystemProfile.getMaxCacheTransNum == 0 || pe.getTransPoolMgr.getTransLength() < SystemProfile.getMaxCacheTransNum) {
              pe.getTransPoolMgr.putTran(t)
              //广播接收交易事件
              if (pe.getTransPoolMgr.getTransLength() <= SystemProfile.getMinBlockTransNum)
                getActorRef(pe.getSysTag, GlobalUtils.ActorType.VOTER_MODULE) ! VoteRecover
            }
            
        }
      }
    case _ => //ignore
  }
}
