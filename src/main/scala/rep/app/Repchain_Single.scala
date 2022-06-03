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
import rep.app.management.{ReasonOfStartup, RepChainMgr}
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType
import sun.misc.Signal

/**
 * Repchain app start
 * Created by User on 2017/9/24.
 */
object Repchain_Single {

  def main(args: Array[ String ]): Unit = {
    if(args!=null && args.length>0){
      args.foreach(name=>{
        System.out.println(s"Start start node(${name})...")
        RepChainMgr.Startup4Single(name,ReasonOfStartup.Manual)
        System.out.println(s"Now start to check whether the node(${name}) is started successfully...")
        System.out.println(s"Node(${name}) , startup result=${RepChainMgr.systemStatus(name)}")
      })
    } else{
      System.out.println("Please enter the node name to start, for example：Repchain_Single 121000005l35120456.node1 330597659476689954.node6")
    }
  }
}

