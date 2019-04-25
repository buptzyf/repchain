package rep.network.util

import akka.actor.{ ActorRef, Props }
import rep.app.conf.{ SystemProfile }
import scala.util.control.Breaks._

object NodeHelp {
  def isSameNodeForRef(srcRef: ActorRef, destRef: ActorRef): Boolean = {
    if (srcRef == null) false
    if (destRef == null) false
    val srcStr = getNodePath(srcRef)
    val destStr = getNodePath(destRef)
    isSameNodeForString(srcStr, destStr)
  }

  def isSameNodeForString(srcStr: String, destStr: String): Boolean = {
    var b: Boolean = false
    if (srcStr.indexOf("/user") > 0) {
      val addr = srcStr.substring(0, srcStr.indexOf("/user"))
      b = destStr.indexOf(addr) != -1
    }
    b
  }
  
  def getNodePath(actref: ActorRef):String={
    if(actref == null) ""
    akka.serialization.Serialization.serializedActorPath(actref)
  }
  
  def ConsensusConditionChecked(inputNumber: Int, nodeNumber: Int): Boolean = {
    if ((inputNumber - 1) >= Math.floor(((nodeNumber)*1.0) / 2)) true else false
  }
  
  def isCandidateNow(Systemname: String, candidates: Set[ String ]): Boolean = {
    val list = candidates.toList
    list.exists(p=> Systemname == p)
  }
  
  def isBlocker(blockerOfInput: String, blockername: String): Boolean = {
    if(blockerOfInput == blockername){
      true
    }else{
      false
    }
  }
  
  def checkBlocker(myaddress:String,sendaddress:String):Boolean = {
    var b :Boolean = false
    if(myaddress.indexOf("/user")>0){
      val addr = myaddress.substring(0, myaddress.indexOf("/user"))
      b = sendaddress.indexOf(addr) != -1
    }
    b
  }

  def isSeedNode(nodeName:String):Boolean={
    SystemProfile.getGenesisNodeName.equals(nodeName)
  }
  
  def isCandidatorNode(roles: Set[String]):Boolean = {
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
  
  def getNodeName(roles: Set[String]):String = {
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
  
}