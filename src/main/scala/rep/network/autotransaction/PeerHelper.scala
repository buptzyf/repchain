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

package rep.network.autotransaction

import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
import rep.app.conf.SystemProfile
import rep.crypto.cert.SignTool
import rep.log.RepLogger
import rep.network.base.ModuleBase
import rep.protos.peer._
import rep.utils.{IdTool, TimeUtils}
import rep.network.module.ModuleActorType
import rep.utils.GlobalUtils.EventType
/**
 *
 * 代理节点辅助类
 * 用来定时生成Transaction
 * 之后也应该通过其接收前端数据进而生成Transaction
 *
 * @author jiangbuyun
 */

object Topic {
  val Transaction = "Transaction"
  val Block = "Block"
  val Event = "Event"
  val Endorsement = "Endorsement"
  val SyncOfTransaction = "SyncOfTransaction"
  val SyncOfBlock = "SyncOfBlock"
  val VoteTransform = "VoteTransform"
}

object InnerTopic {
  val BlockRestore = "BlockRestore"
}

object PeerHelper {

  def props(name: String): Props = Props(classOf[PeerHelper], name)

  case object Tick
  case object TickInit
  case object TickInvoke
  case object TickQuery

  /**
   * 采用节点私钥创建交易的方法
   *
   */
  def createTransaction4Invoke(nodeName: String, chaincodeId: ChaincodeId,
                               chaincodeInputFunc: String, params: Seq[String]): Transaction = {
    var t: Transaction = new Transaction()
    val millis = TimeUtils.getCurrentTime()
    if (chaincodeId == null) t

    val txid = IdTool.getRandomUUID
    val cip = new ChaincodeInput(chaincodeInputFunc, params)
    t = t.withId(txid)
    t = t.withCid(chaincodeId)
    t = t.withIpt(cip)
    t = t.withType(rep.protos.peer.Transaction.Type.CHAINCODE_INVOKE)
    t = t.clearSignature
    val certid = IdTool.getCertIdFromName(nodeName)
    var sobj = Signature(Option(certid), Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)))
    sobj = sobj.withSignature(ByteString.copyFrom(SignTool.sign(nodeName, t.toByteArray)))

    t = t.withSignature(sobj)

    t
  }

  def createTransaction4Deploy(nodeName: String, chaincodeId: ChaincodeId,
                               spcPackage: String, legal_prose: String, timeout: Int,
                               ctype: rep.protos.peer.ChaincodeDeploy.CodeType): Transaction = {
    var t: Transaction = new Transaction()
    val millis = TimeUtils.getCurrentTime()
    if (chaincodeId == null) t

    val txid = IdTool.getRandomUUID
    var cip = new ChaincodeDeploy(timeout)
    cip = cip.withCodePackage(spcPackage)
    cip = cip.withLegalProse(legal_prose)
    cip = cip.withCtype(ctype)
    t = t.withId(txid)
    t = t.withCid(chaincodeId)
    t = t.withSpec(cip)
    t = t.withType(rep.protos.peer.Transaction.Type.CHAINCODE_DEPLOY)
    t = t.clearSignature
    
    val certid = IdTool.getCertIdFromName(nodeName)
    var sobj = Signature(Option(certid), Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)))
    sobj = sobj.withSignature(ByteString.copyFrom(SignTool.sign(nodeName, t.toByteArray)))

    t = t.withSignature(sobj)

    t
  }
  
  def createTransaction4State(nodeName: String, chaincodeId: ChaincodeId,
                               state:Boolean): Transaction = {
    var t: Transaction = new Transaction()
    val millis = TimeUtils.getCurrentTime()
    if (chaincodeId == null) t

    val txid = IdTool.getRandomUUID
    t = t.withId(txid)
    t = t.withCid(chaincodeId)
    t = t.withType(rep.protos.peer.Transaction.Type.CHAINCODE_SET_STATE)
    t = t.withState(state)
    t = t.clearSignature
    val certid = IdTool.getCertIdFromName(nodeName)
    var sobj = Signature(Option(certid), Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)))
    sobj = sobj.withSignature(ByteString.copyFrom(SignTool.sign(nodeName, t.toByteArray)))

    t = t.withSignature(sobj)
    t
  }

}

class PeerHelper(name: String) extends ModuleBase(name) {
  import PeerHelper._
  import context.dispatcher

  import scala.concurrent.duration._

  //val si1 = scala.io.Source.fromFile("scripts/example_invoke_" + pe.getSysTag + ".js")
  //val li1 = try si1.mkString finally si1.close()
  val si2 = scala.io.Source.fromFile("api_req/json/transfer_" + pe.getSysTag + ".json","UTF-8")
  val li2 = try si2.mkString finally si2.close()
  //val sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  var chaincode:ChaincodeId = new ChaincodeId("ContractAssetsTPL",1)

  override def preStart(): Unit = {
    //注册接收交易的广播
    //SubscribeTopic(mediator, self, selfAddr, Topic.Transaction, true)
    RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix("Transaction Creator Start"))
    scheduler.scheduleOnce(15.seconds, self, Tick)
  }

  // override postRestart so we don't call preStart and schedule a new Tick
  override def postRestart(reason: Throwable): Unit = ()

  override def receive = {

    case Tick =>
      //val blk = BlockHelper.genesisBlockCreator()
      //chaincode = IdTool.getCid(blk.transactions(0).getCid)
      
      scheduler.scheduleOnce(10.seconds, self, TickInit)

    case TickInit =>
      if (SystemProfile.getTranCreateDur > 0)
        scheduler.scheduleOnce(SystemProfile.getTranCreateDur.millis, self, TickInvoke)
 
    case TickInvoke =>
      try {
        //createTransForLoop //在做tps测试到时候，执行该函数，并且注释其他代码
        val t3 = createTransaction4Invoke(pe.getSysTag, chaincode,
          "transfer", Seq(li2))
        //pe.getActorRef(ModuleActorType.ActorType.transactionpool) ! t3
        mediator ! Publish(Topic.Transaction, t3)
         RepLogger.trace(RepLogger.System_Logger,this.getLogMsgPrefix(s"########################create transaction id =${t3.id}"))
      } catch {
        case e: RuntimeException => throw e
      }
      scheduler.scheduleOnce(SystemProfile.getTranCreateDur.millis, self, TickInvoke)
  }

  //自动循环不间断提交交易到系统，用于压力测试或者tps测试时使用。
  def createTransForLoop = {
    var count: Int = 0;
    if (pe.getSysTag == "121000005l35120456.node1"|| pe.getSysTag == "12110107bi45jh675g.node2" || pe.getSysTag=="122000002n00123567.node3" || 
        pe.getSysTag=="921000005k36123789.node4" || pe.getSysTag=="921000006e0012v696.node5")
      while (true) {
        try {
          val start = System.currentTimeMillis()
          //val start = System.currentTimeMillis()
          //todo 在运行时需要传送正确的chaincodename
          //val chaincodeId = new ChaincodeId("chaincode-name", 1)
          val t3 = createTransaction4Invoke(pe.getSysTag, chaincode,
          "transfer", Seq(li2))
          //pe.getActorRef(ActorType.transactionpool) ! t3
          pe.getTransPoolMgr.putTran(t3,pe.getSysTag)
          //mediator ! Publish(Topic.Transaction, t3)
          //RepLogger.trace(RepLogger.System_Logger,this.getLogMsgPrefix(s"########################create transaction id =${t3.id}"))
          count += 1
          if (count > 1000) {
            val end = System.currentTimeMillis()
            RepLogger.trace(RepLogger.System_Logger,"send 1000 trans spent = " + (end - start))
            //Thread.sleep(1000)
            count = 0
          }
          //val end = System.currentTimeMillis()
          //println(s"!!!!!!!!!!!!!!!!!!!!auto create trans time=${end-start}")
        } catch {
          case e: RuntimeException => throw e
        }
      }
  }
}
