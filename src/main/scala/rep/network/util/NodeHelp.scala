package rep.network.util

import akka.actor.{ ActorRef, Props }

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
  
}