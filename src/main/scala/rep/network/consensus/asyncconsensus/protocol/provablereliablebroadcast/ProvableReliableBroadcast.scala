package rep.network.consensus.asyncconsensus.protocol.provablereliablebroadcast


import java.util.concurrent.ConcurrentHashMap
import akka.actor.ActorRef
import com.google.protobuf.ByteString
import rep.crypto.Sha256
import rep.log.RepLogger
import rep.network.consensus.asyncconsensus.common.erasurecode.ErasureCodeFactory
import rep.network.consensus.asyncconsensus.common.merkle.MerkleTree
import rep.network.consensus.asyncconsensus.protocol.msgcenter.{Broadcaster}
import rep.protos.peer.DataOfPRBC.provableReliableType
import rep.protos.peer.{DataOfPRBC, DataOfStripe, ReadyOfPRBC, ResultOfPRBC, StartOfPRBC}
import scala.collection.JavaConverters._

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-03-13
 * @category	可验证的可靠广播协议实现。
 */

class ProvableReliableBroadcast(nodeName:String,pathschema:String, moduleSchema:String, numberOfNodes:Int, numberOfFault:Int,broadcaster: Broadcaster,caller:ActorRef) {
   //private val moduleName = moduleSchema1.replaceAll("\\{nodeName\\}",nodeName)
   //val tmp = moduleSchema1.substring(0,moduleSchema1.lastIndexOf("{nodeName}"))
   //val tmp1 =moduleSchema1.substring(moduleSchema1.lastIndexOf("{nodeName}"),moduleSchema1.length)
   val moduleName = this.moduleSchema.substring(this.moduleSchema.lastIndexOf("-")+1,this.moduleSchema.length)//tmp1.replace("{nodeName}",nodeName)
   //private val moduleSchema = tmp + moduleName
   private val moduleNodeName = Broadcaster.getModuleNodeName(moduleName)

   private val EchoThreshold   = numberOfNodes - numberOfFault
   private val ReadyThreshold  = numberOfFault + 1
   private val OutputThreshold = 2 * numberOfFault + 1

   private var data = new ConcurrentHashMap[String, PRBCData] asScala

  println(s"create ProvableReliableBroadcast nodename=${this.nodeName},processname=${this.moduleSchema}")

   private def isLeader:Boolean={
      if(this.nodeName == this.moduleNodeName){
         true
      }else{
         false
      }
   }

   def StartHandler(msg:StartOfPRBC):Unit={
     println(s"start  ProvableReliableBroadcast nodename=${this.nodeName},processname=${this.moduleName},msg node=${msg.sendName}")
      if(isLeader && msg.sendName == this.moduleNodeName){
         val encoder = ErasureCodeFactory.generateErasureCoder(this.numberOfNodes - this.numberOfFault,this.numberOfFault)
         val stripes = encoder.encode(msg.transationsCipher.toByteArray)
         this.broadcaster.broadcastForEachInSchema(this.pathschema,this.moduleSchema,PackagedValMsg(stripes,msg))
      }
   }

   private def PackagedValMsg(stripes:Array[DataOfStripe],start:StartOfPRBC):Array[Any]={
      var vals = new Array[Any](stripes.length)
      var i = 0
      for(i<-0 to stripes.length-1){
         vals(i) = new DataOfPRBC(this.nodeName,start.sendName,provableReliableType.VAL, Some(stripes(i)),start.round)
      }
      vals
   }

   def recvHandler(msg:DataOfPRBC):Unit={
      if(msg.typeOfMsg == provableReliableType.VAL){
         this.HandlerOfVal(msg)
      }else if(msg.typeOfMsg == provableReliableType.ECHO){
         this.HandlerOfEcho(msg)
      }
   }

   private def HandlerOfVal(valMsg:DataOfPRBC):Unit={
     println(s"val  msg nodename=${valMsg.sendName},processname=${valMsg.moduleName},recver=${this.nodeName}")
      if(valMsg.moduleName == this.moduleNodeName && valMsg.typeOfMsg == provableReliableType.VAL  && valMsg.stripe != None){
         val rootHash = valMsg.stripe.get.rootHash.toStringUtf8
         val d = this.getAndSetData(rootHash,valMsg.round)
            if(MerkleTree.merkleVerify(this.numberOfNodes,valMsg.stripe.get)){
               val echoMsg = new DataOfPRBC(this.nodeName,valMsg.moduleName,provableReliableType.ECHO, valMsg.stripe,valMsg.round)
               this.broadcaster.broadcastInSchema(this.pathschema,this.moduleSchema,echoMsg)
            }else{
               RepLogger.trace(RepLogger.Consensus_Logger,s"nodename=${this.nodeName},processname=${this.moduleName} in HandlerOfVal:verify error,valMsg=${valMsg.toString}")
            }
      }else{
         RepLogger.trace(RepLogger.Consensus_Logger,s"nodename=${this.nodeName},processname=${this.moduleName} in HandlerOfVal:find invalid msg,valMsg=${valMsg.toString}")
      }
   }

   private def HandlerOfEcho(echoMsg:DataOfPRBC):Unit={
     println(s"echo  msg nodename=${echoMsg.sendName},processname=${echoMsg.moduleName},recver=${this.nodeName}")
      if(echoMsg.moduleName == this.moduleNodeName && echoMsg.typeOfMsg == provableReliableType.ECHO  && echoMsg.stripe != None){
         val rootHash = echoMsg.stripe.get.rootHash.toStringUtf8
         val d = this.getAndSetData(rootHash,echoMsg.round)
         if(d.hasEchoData(echoMsg.sendName) || d.hasEchoSender(echoMsg.sendName)){
            RepLogger.trace(RepLogger.Consensus_Logger,s"nodename=${this.nodeName},processname=${this.moduleName} in HandlerOfEcho:already recv,echoMsg=${echoMsg.toString}")
         }else{
            if(MerkleTree.merkleVerify(this.numberOfNodes,echoMsg.stripe.get)){
               d.saveEchoData(echoMsg.sendName,echoMsg.stripe.get)
            }else{
               RepLogger.trace(RepLogger.Consensus_Logger,s"nodename=${this.nodeName},processname=${this.moduleName} in HandlerOfEcho:verify error,echoMsg=${echoMsg.toString}")
            }
         }

         if(d.getEchoCount >= EchoThreshold && !d.hasSentReady){
            val digest = (s"${this.moduleNodeName}_${rootHash}")
            val tool = d.getSignTool
            val signData = tool.shareSign(digest)
           println(s"node share sign,sign node name=${this.nodeName},sign data=${signData},bytestring=${ByteString.copyFromUtf8(signData)}")
            val readyMsg = new ReadyOfPRBC(this.nodeName,echoMsg.moduleName,ByteString.copyFromUtf8(rootHash),ByteString.copyFromUtf8(signData),echoMsg.round)
            this.broadcaster.broadcastInSchema(this.pathschema,this.moduleSchema,readyMsg)
            d.sentReadyMsg
         }
      }else{
         RepLogger.trace(RepLogger.Consensus_Logger,s"nodename=${this.nodeName},processname=${this.moduleName} in HandlerOfEcho:find invalid msg,echoMsg=${echoMsg.toString}")
      }
   }

   def HandlerOfReady(readyMsg:ReadyOfPRBC):Unit={
     println(s"ready  msg nodename=${readyMsg.sendName},processname=${readyMsg.moduleName},recver=${this.nodeName}")
      if(readyMsg.moduleName == this.moduleNodeName ){
         val rootHash = readyMsg.rootHash.toStringUtf8
         val d = this.getAndSetData(rootHash,readyMsg.round)
         if(d.hasRecvReadySender(readyMsg.sendName)){
            RepLogger.trace(RepLogger.Consensus_Logger,s"nodename=${this.nodeName},processname=${this.moduleName} in HandlerOfReady:already recv,readyMsg=${readyMsg.toString}")
         }else{
            val sigma = readyMsg.shareSign.toStringUtf8
            val tool = d.getSignTool
            val digest = (s"${this.moduleNodeName}_${rootHash}")
           println(s"recv node share sign,sign node name=${readyMsg.sendName},sign data=${sigma},bytestring=${readyMsg.shareSign},verify node=${this.nodeName}")
            if(tool.recvShareSign(sigma,digest)){
               d.saveRecvReadyData(readyMsg.sendName)
            }else{
               RepLogger.trace(RepLogger.Consensus_Logger,s"nodename=${this.nodeName},processname=${this.moduleName} in HandlerOfEcho:verify error,echoMsg=${readyMsg.toString}")
            }
         }

         if(d.getReadyCount >= ReadyThreshold && !d.hasSentReady){
            val digest = (s"${this.moduleNodeName}_${rootHash}")
            val tool = d.getSignTool
            val signData = tool.shareSign(digest)
           println(s"node share sign,sign node name=${this.nodeName},sign data=${signData},bytestring=${ByteString.copyFromUtf8(signData)}")
            val msg = new ReadyOfPRBC(this.nodeName,readyMsg.moduleName,ByteString.copyFromUtf8(rootHash),ByteString.copyFromUtf8(signData),readyMsg.round)
            this.broadcaster.broadcastInSchema(this.pathschema,this.moduleSchema,msg)
            d.sentReadyMsg
         }

         if(d.getReadyCount >= OutputThreshold &&  d.getReadyCount >= (this.numberOfNodes - this.numberOfFault) && d.getSignTool.hasCombine){
           println(s"combine sign  msg ,recver=${this.nodeName}")
            val cmbSign = d.getSignTool.combineSign()
            if(d.getSignTool.verifyCombineSign(cmbSign,(s"${this.moduleNodeName}_${rootHash}"))){
               val sd = d.getStripes
               val erasure = ErasureCodeFactory.generateErasureCoder(this.numberOfNodes - this.numberOfFault, this.numberOfFault, 1)
               val decodedata = erasure.decode(sd)
               val msg = new ResultOfPRBC(this.nodeName,readyMsg.moduleName,readyMsg.rootHash,ByteString.copyFrom(decodedata),ByteString.copyFromUtf8(cmbSign),readyMsg.round)
               this.broadcaster.broadcastResultToSpecialNode(this.caller,msg)
            }
         }
      }else{
         RepLogger.trace(RepLogger.Consensus_Logger,s"nodename=${this.nodeName},processname=${this.moduleName} in HandlerOfEcho:find invalid msg,readyMsg=${readyMsg.toString}")
      }
   }

   private def getAndSetData(rootHash:String,round:String):PRBCData={
      var d : Option[PRBCData] = None
      if(!isRecvRootHash(rootHash)){
         d = Some(new PRBCData(round.toLong,rootHash,this.nodeName))
         this.data += rootHash -> d.get
      }else{
         d = this.data.get(rootHash)
      }
      d.get
   }

   private def isRecvRootHash(rootHash:String):Boolean={
      if(this.data.contains(rootHash)){
         val data = this.data.getOrElse(rootHash,null)
         if(data == null){
            false
         }else{
            true
         }
      }else{
         false
      }
   }

}
