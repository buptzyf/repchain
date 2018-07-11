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

import rep.storage._;
import rep.storage.leveldb._;
import rep.protos.peer._;
import rep.utils._
import rep.crypto._
import java.util


/**
 * @author jiangbuyun
 * @version	0.7
 * @since	2017-09-28
 * */
object teststoragemgr {
    def main(args: Array[String]): Unit = {
       
		   /*val s = StorageMgr.GetStorageMgr("1")
		   val s1 = StorageMgr.GetStorageMgr("2")
		   val prefix = "c_"
		   val ccid = "sfsldfsffkwiiwuqoq_"
		   s.BeginTrans()
		   s1.BeginTrans()
		   for(a <- 1 to 1000){
		     val b = (new util.Random()).nextInt(9000)
		     val key = prefix+ccid+b
		     s.put(key, String.valueOf("s"+b).getBytes)
		     s1.put(key, String.valueOf("s1"+b).getBytes)
		   }
		   println("s merkle="+s.GetComputeMerkle4String);
		   println("s1 merkle="+s1.GetComputeMerkle4String);
		   */
		   
		  // val lh = StorageMgr.GetStorageMgr("testSystem");
		   
       
		   /*ls.put("a_1001", "bbbbb".getBytes());
		   println("key=a_1001\tvalue="+ls.get("a_1001"));
		   ls.delete("a_1001");
		    println("key=a_1001\tvalue="+ls.get("a_1001"));*/
		    
			/*lh.put("a_1001", "nnnn".getBytes());
			lh.put("a_1000", "uuuu".getBytes());
			println("key=a_1000\tvalue="+lh.byteToString(lh.get("a_1000")));
			lh.BeginTrans();
			lh.delete("a_1000");
			System.out.println("key=a_1000\tvalue="+lh.byteToString(lh.get("a_1000")));
			lh.put("c_1000", "dddd".getBytes());
			System.out.println("key=c_1000\tvalue="+lh.byteToString(lh.get("c_1000")));
			lh.put("c_1000", "eeee".getBytes());
			System.out.println("key=c_1000\tvalue="+lh.byteToString(lh.get("c_1000")));
			System.out.println("in trans");
			lh.printmap(lh.FindByLike("a_"));
			System.out.println("");
			lh.printmap(lh.FindByLike("c_"));
			System.out.println("");
			lh.CommitTrans();
			System.out.println("key=a_1000\tvalue="+lh.byteToString(lh.get("a_1000")));
			System.out.println("key=c_1000\tvalue="+lh.byteToString(lh.get("c_1000")));
			System.out.println("out trnas");
			lh.printmap(lh.FindByLike("a_"));
			System.out.println("");
			lh.printmap(lh.FindByLike("c_"));*/
		   
		  /* 
		   val atemp = s.FindByLike("c_")
          atemp.foreach(f=>{
            val key = f._1
            val value1 = s.get(key)
            val value2 = s1.get(key)
            println("key="+key+"\tvalue1="+BytesHex.bytes2hex(value1)+"\tvalue2="+BytesHex.bytes2hex(value2))
                //+"\tvalue2="+BytesHex.bytes2hex(value2)
            //println(f._1, String.valueOf(f._2))
          })
          
          println(s.GetComputeMerkle4String)
          println(s1.GetComputeMerkle4String)
      
      s.BeginTrans()
      s.put("c_key_a", "a".getBytes)
      s.put("c_key_b", "d".getBytes)
      println(s.GetComputeMerkle4String)
      s.RollbackTrans()
      s1.BeginTrans()
      s1.put("c_key_a", "a".getBytes)
      s1.put("c_key_b", "d".getBytes)
      println(s1.GetComputeMerkle4String)
      s1.RollbackTrans()*/
      
     /*println(s.getBlockHeight())
      val blk = s.getBlockByHeight(1)
      val blkobj = Block.parseFrom(blk);
		  println(blkobj.toString())
		  
		  println(s1.getBlockHeight())
      val blk1 = s1.getBlockByHeight(1)
      val blkobj1 = Block.parseFrom(blk1);
		  println(blkobj1.toString())*/
		                        
		  /* val tidVal =  s.get("c_" + "267454856593c8a7f9941e723767801d67e42130aa81d5720fddac932c39134e")
      
      val tid = SerializeUtils.deserialise(tidVal)
      println(tid)
      val txId = SerializeUtils.deserialise(tidVal).asInstanceOf[String]
		  println(txId)*/
		  
		}
}