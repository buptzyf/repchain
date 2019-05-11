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
  
  //获取Actor的地址，akka.ssl.tcp://Repchain@192.168.10.155:54310
  def getNodeAddress(actref: ActorRef):String={
    val path = getNodePath(actref)
    path.substring(0, path.indexOf("/user"))
  }
  
  //获取Actor的路径，akka.ssl.tcp://Repchain@192.168.10.155:54310/user/modulemanager/synchresponser#-1500748370
  def getNodePath(actref: ActorRef):String={
    if(actref == null) ""
    akka.serialization.Serialization.serializedActorPath(actref)
  }
  
  def ConsensusConditionChecked(inputNumber: Int, nodeNumber: Int): Boolean = {
    (inputNumber - 1) >= Math.floor(((nodeNumber)*1.0) / 2)
  }
  
  def isCandidateNow(Systemname: String, candidates: Set[ String ]): Boolean = {
    val list = candidates.toList
    list.contains(Systemname)
  }
  
  def isBlocker(blockerOfInput: String, blockername: String): Boolean = {
    blockerOfInput == blockername
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