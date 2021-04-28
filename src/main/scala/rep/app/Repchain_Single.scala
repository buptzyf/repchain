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
import sun.misc.Signal

/**
 * Repchain app start
 * Created by User on 2017/9/24.
 */
object Repchain_Single {

  def getOSSignalType:String={
    if (System.getProperties().getProperty("os.name").toLowerCase().startsWith("win")){
      "INT"
    } else{
      "USR2"
    }
  }

  def main(args: Array[ String ]): Unit = {
    var systemTag = "1"
    if(args!=null && args.length>0) systemTag = args(0)
    val sig = new Signal(getOSSignalType)
    Signal.handle(sig, new shutdownHandler(systemTag))
    RepChainMgr.Startup4Single(systemTag)
    /*val sys1 = new ClusterSystem(systemTag, InitType.SINGLE_INIT,true)
    sys1.init
    val joinAddress = sys1.getClusterAddr
    sys1.joinCluster(joinAddress)
    sys1.start*/
  }
}

