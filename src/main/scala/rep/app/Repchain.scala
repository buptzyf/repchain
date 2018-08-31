/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Fintech Research Center of ISCAS.
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

  var coapServerNum = 1;

  def main(args: Array[String]): Unit = {

    //创建系统实例
    val sys1 = new ClusterSystem("1",InitType.MULTI_INIT,true)
    sys1.init//初始化（参数和配置信息）
    val joinAddress = sys1.getClusterAddr//获取组网地址
    sys1.joinCluster(joinAddress)//加入网络
    sys1.enableWS()//开启API接口
    sys1.start//启动系统

    val cluster = sys1.getActorSys//获取内部系统SystemActor实例

    val node_min = 4
    //如果node_max>node_min 将启动node反复离网和入网的仿真，但是由于system离网后无法复用并重新加入
    //运行一定时间会内存溢出
    val node_max = 4
    var node_add = true

    var nodes = Set.empty[ClusterSystem]
    nodes+= sys1

    var nodes_off = Set.empty[ClusterSystem]

    for(i <- 2 to node_max) {
      Thread.sleep(10000)
      val len = nodes.size
      coapServerNum += 1;
      val sys = new ClusterSystem(i.toString,InitType.MULTI_INIT,true)
      sys.init
      sys.joinCluster(joinAddress)
      sys.start
      nodes += sys
    }

    //node数量在最大和最小值之间振荡,仿真node入网和离网
    //离网的system似乎无法复用,只能重新新建实例
    if(node_max > node_min){
      var node_add = false
      for(i <- 1 to 1000) {
        Thread.sleep(5000)
        val len = nodes.size
        if(len >= node_max){
          node_add=false
        }else if(len <= node_min){
          node_add=true
        }
        if(!node_add){
          val nd_system = nodes.last
          //nd_system.terminate()
          //nd_system.shutdown();
          //Cluster(system0).down(Cluster(nd_system).selfAddress)
          nd_system.leaveCluster(cluster)
          try{
            //Await.ready(nd_system.terminate(), Duration.Inf)
          }catch{
            case msg:InvalidAssociationException => msg
          }
          //Await.ready(nd_system.whenTerminated, 30.seconds)
          //nd_system.terminate();
          //nd_system.shutdown()
          nodes -= nd_system
          //nodes_off += nd_system
        } else{
          //避免systemName重复
          val sys = new ClusterSystem(i.toString,InitType.MULTI_INIT,true)
          sys1.init
          val joinAddress = sys1.getClusterAddr
          sys1.joinCluster(joinAddress)
          sys1.start
          nodes += sys
          //nodes_off -= nd_system
        }
      }
    }
  }
}
