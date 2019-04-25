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

package rep.storage.test

import rep.storage._
import scala.collection.mutable
import scala.collection.immutable
import rep.utils._
import scala.collection.mutable.ArrayBuffer;


/**
 * @author jiangbuyun
 * @version	0.7
 * @since	2017-09-28
 * */
object testmap extends Object {
  
  def test1{
     var map : mutable.LinkedHashMap[String,String] = new mutable.LinkedHashMap[String,String]()
    map += "a"->"9"
    map += "c"->"1"
    map += "d"->"2"
    
    var map0 : mutable.LinkedHashMap[String,String] = new mutable.LinkedHashMap[String,String]()
    map0 += "a0"->"9"
    map0 += "c0"->"1"
    map0 += "d0"->"2"
    
    var map2 : mutable.LinkedHashMap[String,mutable.LinkedHashMap[String,String]] = 
                                      new mutable.LinkedHashMap[String,mutable.LinkedHashMap[String,String]]()
    map2 += "cid1"->map
    map2 += "cid2"->map0
    
    map2.foreach( f  => {
      println(f)
    })
    
    var mbs : Array[Byte] = SerializeUtils.serialise(map2)
    
    var map3 = SerializeUtils.deserialise(mbs)
    
    if(map3.isInstanceOf[mutable.LinkedHashMap[String,mutable.LinkedHashMap[String,String]]] ){
      var tmpmap = map3.asInstanceOf[mutable.LinkedHashMap[String,mutable.LinkedHashMap[String,String]]]
      //
      var map4 : mutable.LinkedHashMap[String,String] = new mutable.LinkedHashMap[String,String]()
      map4 += "a4"->"9"
      map4 += "c4"->"1"
      map4 += "d4"->"2"
      tmpmap += "e"->map4
      tmpmap.-=("cid2")
      map -= ("d")
      map += "a"->"100"
      
      println(map4.size.toString())
      
      
      tmpmap += "cid1"->map
      tmpmap.foreach( f  => {
        println(f)
      })
      val a = tmpmap.toArray
      a.foreach(f => {
        println(f)
      })
      
      var ad:Array[String] = Array()
      var cd:ArrayBuffer[String] = new ArrayBuffer[String]()
      tmpmap.foreach( f  => {
        ad = f._2.values.toArray
        cd ++= ad
        
        
      })
      
      //cd.remove(3)
      
      cd.foreach(d=>{
          println(d)
        })
      
        val cid = "c_abac_key_1"
        val splitstrs:Array[String] = cid.split("_", 3)
       /* splitstrs.foreach(f=>{
          println(f);
        })*/
     
        if(splitstrs.length == 3){
          println(splitstrs(0))
          println(splitstrs(1))
          println(splitstrs(2))
        }
      
      
      var numArrayBuffer = new ArrayBuffer[Int]();  
  
      //变长数组追加   
      numArrayBuffer +=1  
      numArrayBuffer +=4  
      numArrayBuffer +=2  
      numArrayBuffer +=5  
      numArrayBuffer +=(6,7,8)  
       
        
        
      //for( i <- numArrayBuffer){  
      //    println(i)  
      //}  
      println(numArrayBuffer.mkString(","))  
      println("====================")  
        
        
      //移除最后的三个元素  
      numArrayBuffer.trimEnd(2); 
      println(numArrayBuffer.mkString(","))  
      //移除开始的两个元素  
      numArrayBuffer.trimStart(2);  
      println(numArrayBuffer.mkString(","))  
      //插入 在第2个位置插入100  
      numArrayBuffer.insert(2,100); 
      println(numArrayBuffer.mkString(","))  
      //移除 从3开始移除，移除4个  
      numArrayBuffer.remove(3,1);  
      println(numArrayBuffer.mkString(","))  
        
      //for( i <- numArrayBuffer){  
      //    println(i)  
      //}  
      println(numArrayBuffer.mkString(","))  
      println("====================")  
      
    }
    
    val mapr : mutable.HashMap[String,String]= mutable.HashMap("aaa"->"4444","iii"->"uijj");
    println(mapr("aaa"))
    val tuple = (1,mapr)
    var tuplebs : Array[Byte] = SerializeUtils.serialise(tuple)
    
    var tupletmp = SerializeUtils.deserialise(tuplebs)
    if(tupletmp != null){
     if(tupletmp.isInstanceOf[Tuple2[Integer,mutable.HashMap[String,String]]]){
        var mytuple = tupletmp.asInstanceOf[Tuple2[Integer,mutable.HashMap[String,String]]]
        println(mytuple)
     }
    }
    
    
    /*val map : mutable.HashMap[String,Any]= mutable.HashMap("aaa"->"4444","iii"->"uijj");
    //val ju = new JsonUtil();
    val jstr = JsonUtil.map2Json(map.toMap);
    println(jstr);
    var map2 = new mutable.HashMap[String, Any];
    
    var m:Map[String,Any] = JsonUtil.json2Map(jstr);
    m += "aaa"->"888";
    m += "bbb"->"888";
    var ab = new mutable.ArrayBuffer[String]();
    ab.+=("ssfsf");
    ab.+=("44444");
    ab.+=("66666");
    m += "myarray"->ab;
    
    m.foreach(f => {
      map2.put(f._1, f._2)
    })
   
    val jstr1 = JsonUtil.map2Json(map2.toMap);
    
    println(jstr1);
    
    //println(map2.get("myarray"));
    
    val ar = map2.get("myarray");
    
   
    ar.foreach( f  => {
      println(f);
    })*/
  }
  
  def GetIndex4Key(key:String,tm:immutable.TreeMap[String,String]):Integer={
    var idx = -1
    val ks = tm.keysIterator
    
    idx
  }
  
  def testTreeMap{
    var tm : immutable.TreeMap[String,Array[Byte]] = new immutable.TreeMap[String,Array[Byte]]()
    tm += "b"->"7".getBytes
    tm += "c"->"6".getBytes
    tm += "a"->"9".getBytes 
    println(tm)
    tm += "a"->"5".getBytes
    tm += "d"->"10".getBytes
    tm += "e"->"12".getBytes
    println(tm)
    val v = tm.view(0, 3)
    tm += "f"->"13".getBytes
    v.foreach(f=>{
      println(f);
    })
    //val av = v.toBuffer
    //println(av)
    
    val ts = tm.slice(1, 3)
    println(ts)
    val tsb = ts.values.toBuffer
    println(tsb)
    
    //val r = tm.range("b", "e")
    //println(r)
    
    
    
    val ks = tm.keysIterator
    val idx = ks.indexOf("b")
    println("b idx ="+idx)
    var xs : Array[String] = new Array[String](tm.size-idx)
    val vs = tm.values.view(idx, idx+3) 
    val vsa = vs.toBuffer
    println("b value="+vsa.mkString(","))
    xs.foreach(f=>{
      println(f)
      
    })
    
    val k = tm.keysIteratorFrom("c")
    k.foreach(f=>{
      println(f)
    })
    
    
   
  }
  
def testTreeMapclear{
    var tm : immutable.TreeMap[String,Array[Byte]] = new immutable.TreeMap[String,Array[Byte]]()
    tm += "b"->"7".getBytes
    tm += "c"->"6".getBytes
    tm += "a"->"9".getBytes 
    println(tm)
    
    println(tm)
}

case class myobject(txid:String,name:String)

  def  main(args: Array[String]): Unit = {
  //import rep.sc.Shim.Oper
   //testTreeMap
   //testTreeMapclear
   /*var as = new Array[myobject](5)
   as(0) = new myobject("1","name1")
   as(1) = new myobject("2","name2")
   as(2) = new myobject("3","name3")
   as(3) = new myobject("2","name2")
   as(4) = new myobject("5","name5")
  
   val tmp = as.distinct
   println("tmpsize="+tmp.length+",assize="+as.length)*/
  
  
  }
}