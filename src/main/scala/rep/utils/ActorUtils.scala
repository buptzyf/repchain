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

package rep.utils

/**
  * Created by shidianyue on 2017/6/12.
  */
object ActorUtils {

  /**
    * Get (ip,port) of this system from Remote path
    * Example:
    * clusterPath:akka.ssl.tcp://repChain_@192.168.100.93:53486/user/pm_#-1893758935
    * result:(192.168.100.93,53486)
    * @param clusterPath
    * @return
    */
  def getIpAndPort(clusterPath:String): (String, String) ={
    var str = clusterPath.substring(clusterPath.indexOf("@")+1)
    str = str.substring(0,str.indexOf("/"))
    val re = str.split(":")
    (re(0),re(1))
  }

  def isHelper(path:String):Boolean = {
    path.contains("helper")
  }

  def isAPI(path:String):Boolean = {
    path.contains("api")
  }
}
