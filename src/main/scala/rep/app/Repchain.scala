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

import akka.remote.transport.Transport.InvalidAssociationException
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType

/**
  * Repchain app start
  * @author c4w 2017/9/24.
  */
object Repchain {

  def main(args: Array[String]): Unit = {

    //创建系统实例
     var nodelist : Array[String] = new Array[String] (4)
     nodelist(0) = "12110107bi45jh675g.node2"
     nodelist(1) = "122000002n00123567.node3"
     nodelist(2) = "921000005k36123789.node4"
     nodelist(3) = "921000006e0012v696.node5"
     
    val sys1 = new ClusterSystem("121000005l35120456.node1",InitType.MULTI_INIT,true)
    sys1.init//初始化（参数和配置信息）
    val joinAddress = sys1.getClusterAddr//获取组网地址
    sys1.joinCluster(joinAddress)//加入网络
    sys1.enableWS()//开启API接口
    sys1.start//启动系统

    //val cluster = sys1.getActorSys//获取内部系统SystemActor实例

    val node_min = 5
    //如果node_max>node_min 将启动node反复离网和入网的仿真，但是由于system离网后无法复用并重新加入
    //运行一定时间会内存溢出
    val node_max = 5
    var node_add = true

    var nodes = Set.empty[ClusterSystem]
    nodes+= sys1

    var nodes_off = Set.empty[ClusterSystem]

     var tmpsystem : ClusterSystem = null
     
    for(i <- 2 to node_max) {
      Thread.sleep(2000)
      
      val len = nodes.size
      val sys = new ClusterSystem(nodelist(i-2),InitType.MULTI_INIT,true)
      sys.init
      sys.joinCluster(joinAddress)
      sys.disableWS()
      sys.start
      nodes += sys
      if(i == 5){
        tmpsystem = sys
      }
    }
     
   /*  Thread.sleep(1000*60*3)
     tmpsystem.shutdown
     
     
     Thread.sleep(1000*60*2)
     val sys = new ClusterSystem(nodelist(3),InitType.MULTI_INIT,true)
      sys.init
      sys.joinCluster(joinAddress)
      sys.disableWS()
      sys.start*/
     
  }
}
