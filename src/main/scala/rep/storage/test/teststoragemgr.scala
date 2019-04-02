/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
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

package rep.storage.test

import rep.storage._;
import rep.storage.leveldb._;
import rep.protos.peer._;
import rep.utils._
import rep.crypto._
import java.util._
import com.google.protobuf.ByteString


/**
 * @author jiangbuyun
 * @version	0.7
 * @since	2017-09-28
 * */
object teststoragemgr {
   def getAllKeyValue():Unit={
    val sr = ImpDataAccess.GetDataAccess("121000005l35120456.node1")
    val hr = sr.FindByLike("c_")
    hr.foreach(f=>{
      if(f._2 != null){
        try{
          println(s"key=${f._1}\tvalue=${SerializeUtils.deserialise(f._2)}")
        }catch{
          case e:Exception => println(s"key=${f._1}\t")
        }
      }else{
        println(s"key=${f._1}\t")
      }
      //println(s"key=${f._1}|||||\tvalue=${ByteString.copyFrom(f._2).toStringUtf8()}\r\n||||||")
      
    })
  }
   
   def writenull():Unit={
    val sr = ImpDataAccess.GetDataAccess("121000005l35120456.node1")
     sr.Put("sdds", None.toArray)
     //sr.Put("sdds", None)
     val s = sr.Get("sdds")
     
     if(s == null){
       println(s.toString())
     }else{
       println(s.length)
     }
  }
  
  def main(args: Array[String]): Unit = {
      // getAllKeyValue()
		  writenull
		}
}