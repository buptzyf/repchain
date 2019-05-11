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

package rep.network.tools

import scala.collection.immutable.{ TreeMap }
import akka.actor.{ Address }
import java.util.concurrent.locks._
import scala.util.control.Breaks._

import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._

class NodeMgr {
  //private val nodesLock: Lock = new ReentrantLock();
  //private val nodesStableLock: Lock = new ReentrantLock();
  //private val candidatorLock: Lock = new ReentrantLock();
  //本地缓存网络节点
  //private var nodes: TreeMap[String, Address] = new TreeMap[String, Address]()
  private implicit var nodes = new ConcurrentHashMap[String, Address] asScala
  //本地缓存稳定的网络节点
  //private var stableNodes: TreeMap[Address, String] = new TreeMap[Address, String]()
  private implicit var stableNodes = new ConcurrentHashMap[Address, String] asScala
  
  //本地上次候选人名单
  //private var candidator: TreeMap[String, String] = new TreeMap[String, String]()
  //private var candidator: Set[String] = Set.empty[String]

  def getNodes: Set[Address] = {
     nodes.values.toArray.toSet
  }

  def putNode(addr: Address): Unit = {
      nodes.put(addr.toString, addr)
  }

  def removeNode(addr: Address): Unit = {
    nodes.remove(addr.toString)
  }

  def resetNodes(nds: Set[Address]): Unit = {
    nodes.clear
    nds.foreach(addr => {
      putNode(addr)
    })
  }

  def getStableNodes: Set[Address] = {
    stableNodes.keys.toSet
  }

  def getStableNodeName4Addr(addr:Address):String={
      stableNodes.get(addr).get
  }
  
  def putStableNode(addr: Address, nodeName: String): Unit = {
    stableNodes.put(addr, nodeName)
  }

  def removeStableNode(addr: Address): Unit = {
    stableNodes.remove(addr)
  }

  def resetStableNodes(nds: Set[(Address, String)]): Unit = {
    stableNodes.clear()
    nds.foreach(addr => {
      putStableNode(addr._1, addr._2)
    })
  }

  def getNodeAddr4NodeName(nodeName: String): Address = {
    var a: Address = null
    breakable(
        stableNodes.foreach(f => {
          if (f._2 == nodeName) {
            a = f._1
            break
          }
        }))
    a
  }

}