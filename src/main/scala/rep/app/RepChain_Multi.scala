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

import java.io.{File, FileFilter}

import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType

import scala.collection.mutable


/**
  * RepChain启动单机超5个节点以上，配合https://gitee.com/BTAJL/RCJava/blob/dev_sdk_preview/src/main/java/com/Example/cert/GenerateJksFiles.java使用
  * @author zyf
  */
object RepChain_Multi {

  def main(args: Array[String]): Unit = {

    val fileDir = new File("jks")
    // 过滤掉非节点node的jks
    val files = fileDir.listFiles(new FileFilter {
      override def accept(file: File): Boolean = {
        val fileName = file.getName
        if (fileName.endsWith("jks") && fileName.indexOf("node") != -1) {
          true
        } else {
          false
        }
      }
    })

    val map = new mutable.HashMap[String, Int]()
    for (i <- 0 until files.length) {
      map.put("node".+((i + 1).toString), i)
    }

    //创建系统实例
    val nodelist = new Array[String](files.length)
    for (i <- 0 until files.length) {
      nodelist(map(files(i).getName.split('.')(1))) = files(i).getName.dropRight(4)
    }

    val sys1 = new ClusterSystem(nodelist(0), InitType.MULTI_INIT, true)
    sys1.init
    //初始化（参数和配置信息）
    val joinAddress = sys1.getClusterAddr //获取组网地址
    sys1.joinCluster(joinAddress) //加入网络
    sys1.enableWS() //开启API接口
    sys1.start //启动系统

    var nodes = Set.empty[ClusterSystem]
    nodes += sys1

    // 可以根据自己的需要将nodelist.length改成对应的节点数
    for (i <- 1 to 4) {
      Thread.sleep(500)
      val sysN = new ClusterSystem(nodelist(i), InitType.MULTI_INIT, true)
      sysN.init
      //初始化（参数和配置信息）
      sysN.joinCluster(joinAddress)
      sysN.disableWS()
      sysN.start
      nodes += sysN
    }

  }
}
