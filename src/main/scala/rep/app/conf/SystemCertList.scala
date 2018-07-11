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

package rep.app.conf

import java.io._

/**
 * @author jiangbuyun
 * @version	0.7
 * @category	获取信任的证书列表，抽签时从此文件中获取。
 * */
object SystemCertList {
  private var mySystemCertList:Set[String] = (new scala.collection.mutable.ArrayBuffer[String]()).toSet[String]
  
  //private def InitSystemCertList:Set[String] = {
    var a = new scala.collection.mutable.ArrayBuffer[String]()
    val fis = new File("jks")
    if(fis.isDirectory()){
      val fs = fis.listFiles()
      for(fn<-fs){
        if(fn.isFile()){
          val fname = fn.getName
          val pos = fname.indexOf("mykeystore_")
          val suffixpos = fname.indexOf(".jks")
          if(pos >= 0 && suffixpos>0){
            a += fname.substring(pos+11, suffixpos)
          }
        }
      }
    }
    mySystemCertList = a.toSet[String]
  //}
  
  def getSystemCertList:Set[String] = {
    //if(this.mySystemCertList.size <=0 ){
    //  mySystemCertList = InitSystemCertList:Set[String]
    //}
    mySystemCertList
  }
  
}