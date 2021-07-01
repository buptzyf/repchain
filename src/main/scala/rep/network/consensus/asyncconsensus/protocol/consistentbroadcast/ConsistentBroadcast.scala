package rep.network.consensus.asyncconsensus.protocol.consistentbroadcast

import java.util.concurrent.ConcurrentHashMap
import akka.actor.ActorRef
import rep.log.RepLogger
import rep.network.consensus.asyncconsensus.protocol.consistentbroadcast.ConsistentBroadcast.{CBC_ECHO, CBC_FINAL, CBC_RESULT, CBC_SEND, StartConsistenBroadcast}
import rep.network.consensus.asyncconsensus.protocol.msgcenter.Broadcaster
import scala.collection.JavaConverters._

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-02-13
 * @category	一致性广播协议体的主要实现。
 */
object ConsistentBroadcast{
  case class StartConsistenBroadcast(ctype:String,round:String,sendName:String,leader:String,content:String)
  case class CBC_SEND(ctype:String,round:String,sendName:String,leader:String,content:String,sign:String)
  case class CBC_ECHO(ctype:String,round:String,sendName:String,leader:String,content:String,sign:String)
  case class CBC_FINAL(ctype:String,round:String,sendName:String,leader:String,content:String,cmbSign:String)
  case class CBC_RESULT(ctype:String,round:String,leader:String,cmbSign:String,content:String)
}

class ConsistentBroadcast(nodeName:String,pathschema:String, moduleSchema:String, numberOfNodes:Int, numberOfFault:Int,broadcaster: Broadcaster,caller:ActorRef) {
  val moduleName = this.moduleSchema.substring(this.moduleSchema.lastIndexOf("-")+1,this.moduleSchema.length)//tmp1.replace("{nodeName}",nodeName)
  private val moduleNodeName = Broadcaster.getModuleNodeName(moduleName)
  private val EchoThreshold = numberOfNodes - numberOfFault

  private var data = new ConcurrentHashMap[String, CBCData] asScala

  private def isLeader:Boolean={
    if(this.nodeName == this.moduleNodeName){
      true
    }else{
      false
    }
  }

  def Start_Handle(startConsistenBroadcast: StartConsistenBroadcast):Unit={
    println(s"start  startConsistenBroadcast nodename=${this.nodeName},processname=${this.moduleName},msg node=${startConsistenBroadcast.sendName}")
    if(isLeader && startConsistenBroadcast.leader == this.moduleNodeName){
      val d = this.getAndSetData(startConsistenBroadcast.round)
      val signTool = d.getSignTool
      val sign = signTool.shareSign(this.moduleNodeName+"_"+startConsistenBroadcast.content)
      signTool.recvShareSign(sign,this.moduleNodeName+"_"+startConsistenBroadcast.content)
      d.saveEchoSender(this.nodeName)
      val msg = new CBC_SEND(startConsistenBroadcast.ctype,startConsistenBroadcast.round,this.nodeName,this.moduleNodeName,startConsistenBroadcast.content,sign)
      this.broadcaster.broadcastExceptSelfInSchema(this.pathschema,this.nodeName,this.moduleSchema,msg)
    }
  }

  def CBC_SEND_Handle(cbc_send:CBC_SEND):Unit={
    println(s"cbc send  msg processname=${cbc_send.sendName},recver=${this.nodeName}")
    if(cbc_send.leader == this.moduleNodeName){
      val content = cbc_send.content
      val d = this.getAndSetData(cbc_send.round)
      val signTool = d.getSignTool
      val sign = signTool.shareSign(this.moduleNodeName+"_"+cbc_send.content)
      val msg = new CBC_ECHO(cbc_send.ctype,cbc_send.round,this.nodeName,this.moduleNodeName,cbc_send.content,sign)
      this.broadcaster.broadcastSpeciaNodeInSchema(this.pathschema,cbc_send.sendName,this.moduleSchema,msg)
    }else{
      RepLogger.trace(RepLogger.Consensus_Logger,s"nodename=${this.nodeName},processname=${this.moduleName} in HandlerOfEcho:find invalid msg,echoMsg=${cbc_send.toString}")
    }
  }


  def CBC_ECHO_Handle(cbc_echo:CBC_ECHO): Unit ={
    println(s"cbc echo  msg processname=${cbc_echo.sendName},recver=${this.nodeName}")
    if(cbc_echo.leader == this.moduleNodeName){
      val content = cbc_echo.content
      val d = this.getAndSetData(cbc_echo.round)
      val signTool = d.getSignTool
      if(!d.hasEchoSender(cbc_echo.sendName)){
        if(signTool.recvShareSign(cbc_echo.sign,this.moduleNodeName+"_"+cbc_echo.content)){
          d.saveEchoSender(cbc_echo.sendName)
        }
      }

      if(d.getEchoSenderCount >= this.EchoThreshold && signTool.hasCombine){
        val combineSign = signTool.combineSign()
        val msg = new CBC_FINAL(cbc_echo.ctype,cbc_echo.round,cbc_echo.sendName,this.moduleNodeName,cbc_echo.content,combineSign)
        this.broadcaster.broadcastInSchema(this.pathschema,this.moduleSchema,msg)
      }
    }else{
      RepLogger.trace(RepLogger.Consensus_Logger,s"nodename=${this.nodeName},processname=${this.moduleName} in HandlerOfEcho:find invalid msg,echoMsg=${cbc_echo.toString}")
    }
  }

  def CBC_FINAL_Handle(cbc_final: CBC_FINAL):Unit={
    println(s"cbc echo  msg processname=${cbc_final.sendName},recver=${this.nodeName}")
    if(cbc_final.leader == this.moduleNodeName){
      val rootHash = cbc_final.content
      val d = this.getAndSetData(cbc_final.round)
      val signTool = d.getSignTool

      if(signTool.verifyCombineSign(cbc_final.cmbSign,this.moduleNodeName+"_"+cbc_final.content)){
        val msg = CBC_RESULT(cbc_final.ctype,cbc_final.round,this.moduleNodeName,cbc_final.cmbSign,cbc_final.content)
        this.caller ! msg
      }
    }else{
      RepLogger.trace(RepLogger.Consensus_Logger,s"nodename=${this.nodeName},processname=${this.moduleName} in HandlerOfEcho:find invalid msg,echoMsg=${cbc_final.toString}")
    }
  }

  private def getAndSetData(round:String):CBCData={
    var d : Option[CBCData] = None
    if(!isRecvRootHash(round)){
      d = Some(new CBCData(round.toLong,this.nodeName))
      this.data += round -> d.get
    }else{
      d = this.data.get(round)
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
