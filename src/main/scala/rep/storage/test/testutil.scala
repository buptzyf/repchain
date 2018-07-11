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

package rep.storage.test

import com.google.protobuf.ByteString
import rep.crypto.Sha256
import scala.collection.mutable.ArrayBuffer
import rep.crypto._
import rep.utils._


/**
 * @author jiangbuyun
 * @version	0.7
 * @since	2017-09-28
 * */
object testutil {
  def main(args: Array[String]): Unit = {
    /*val args = Array("Hello", "world", "it's", "me") 
    val string = args.mkString(" ")
    println(string);
    var a = string.split(" ");
    a(1) = "haha";
    println(a.mkString(" "));*/
    //val vs = "bb86e9ba05d43206e3e27de094a5aeaf28bc8697b6bdfe471851d7f0cbc3fcfe"
    
    
    /*val vs = "ZWYyOTdhOGVlMWZmMGEyZTBlMWE5NTY0NzI3Y2M5MzE4ZGFjOTcwNGFjZmU3NWZkMzBiNmFmM2QyZTkyZjg0ZA=="
    val bs = ByteString.copyFrom(vs.getBytes())
    val bsbs = bs.toByteArray()
    println(new String(bsbs))
    
    val str = bs.toString("UTF-8");
    val bb = str.getBytes
    
    println(new String(bb))
    
    val vsb = vs.getBytes
    
    println(new String(vsb))*/
    
   /* val a = Sha256.hash("sdfs".getBytes());
    println(a.length);
    
    println(Sha256.hashstr("sdfs".getBytes()));
    
    val key = "c_sdfsdfsf_sdfsdf";
    println(key.substring("c_sdfsdfsf_".length(),key.length()))*/
    
   /* for(i <- 1 to 100){
      println("loop="+i)
    }
    
    val s  = "c_sdfsldfl_sflksflsfjf";
    val c = s.hashCode() % 1000
    println("code="+c)*/
    
    var a : ArrayBuffer[String] = new ArrayBuffer[String]()
    a += "sdf"
    a += "sdfsfsdf"
    //a(0) = 0
    //a(1) = 1
    println(a(0)+","+a(1))
    
    val leafbytes = SerializeUtils.serialise(a)
    val tmp = SerializeUtils.deserialise(leafbytes)
    if(tmp.isInstanceOf[ArrayBuffer[String]] ){
            var ta = tmp.asInstanceOf[ArrayBuffer[String]]
            ta.foreach(f=>{
              println(f)
            })
          }
  }
}