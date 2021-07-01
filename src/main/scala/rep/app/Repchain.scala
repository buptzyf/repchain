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

package rep.app

import akka.actor.ActorRef
import akka.remote.transport.Transport.InvalidAssociationException
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType

/**
 * Repchain app start
 * @author c4w 2017/9/24.
 *         -Djavax.net.debug=ssl:handshake:verbose
 */
object Repchain {

  def h4(h:String) = {
    if (h.size >= 4)
      h.substring(0,4)
    else
      h
  }

  def nn(s:String) = {
    var r = ""
    if (s.contains("121000005l35120456.node1")) r = "node1"
    if (s.contains("12110107bi45jh675g.node2")) r = "node2"
    if (s.contains("122000002n00123567.node3")) r = "node3"
    if (s.contains("921000005k36123789.node4")) r = "node4"
    //if (s.contains("921000006e0012v696.node5")) r = "node5"
    r
  }

  def nn(sender:ActorRef) = {
    var r = ""
    val s = sender.path.toString
    if (s.contains("22522")) r = "node1"
    if (s.contains("22523")) r = "node2"
    if (s.contains("22524")) r = "node3"
    if (s.contains("22525")) r = "node4"
    //if (s.contains("22526")) r = "node5"
    r
  }

  def main(args: Array[String]): Unit = {

    //创建系统实例
    var nodelist : Array[String] = new Array[String] (4)
    nodelist(0) = "121000005l35120456.node1"
    nodelist(1) = "12110107bi45jh675g.node2"
    nodelist(2) = "122000002n00123567.node3"
    nodelist(3) = "921000005k36123789.node4"
    //nodelist(4) = "921000006e0012v696.node5"
    var nodeports : Array[Int] = new Array[Int](4)
    nodeports(0) = 22522
    nodeports(1) = 22523
    nodeports(2) = 22524
    nodeports(3) = 22525
    //nodeports(4) = 22526

    var nodehports : Array[Int] = new Array[Int](4)
    nodehports(0) = 9081
    nodehports(1) = 9082
    nodehports(2) = 9083
    nodehports(3) = 9084
    //nodehports(4) = 9085

    for(i <- 0 to 3) {
    //for(i <- 0 to 4) {
      Thread.sleep(5000)
      RepChainMgr.Startup4Multi(nodelist(i),nodeports(i),nodehports(i))
    }


    //以下代码只能在测试系统稳定性，即测试系统离网之后再入网时可以用，发布时一定要删除
    //Thread.sleep(10000)
    //RepChainMgr.StartClusterStub



  }
}
