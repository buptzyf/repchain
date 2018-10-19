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

import rep.storage._
import jnr.ffi.mapper.DataConverter
import rep.crypto.ShaDigest

import scala.collection.mutable
import rep.protos.peer._
import java.io._

import rep.network.consensus.block.BlockHelper
import rep.storage.util.pathUtil

/**
 * @author jiangbuyun
 * @version	0.7
 * @since	2017-09-28
 * */
object test {
  
  def testop={
    val dataaccess :ImpDataAccess = ImpDataAccess.GetDataAccess("1")
    //dataaccess.BeginTrans
    dataaccess.Put("c_sdfs_a", "a".getBytes,true)
    dataaccess.Put("c_sdfs_a1", "b".getBytes,true)
    dataaccess.Put("c_sdfs_a2", "c".getBytes,true)
    dataaccess.Put("a3", "d".getBytes,true)
    //dataaccess.CommitTrans
    //dataaccess.RollbackTrans
    
    dataaccess.printlnHashMap(dataaccess.FindByLike("c"))
    println(dataaccess.GetComputeMerkle4String)
    
  }
  
  def getblockinfo={
    val dataaccess :ImpDataAccess = ImpDataAccess.GetDataAccess("4")
    val info = dataaccess.getBlockChainInfo()
    val b9341 = dataaccess.getBlockIdxByHeight(9341)
    //println("9341="+b9341.toString())
    println("height="+9341+"\tblockhash="+b9341.getBlockHash()+"\tprevioushash="+b9341.getBlockPrevHash())
    val b9342 = dataaccess.getBlockIdxByHeight(9342)
    //println("9342="+b9342.toString())
    println("height="+9342+"\tblockhash="+b9342.getBlockHash()+"\tprevioushash="+b9342.getBlockPrevHash())
    
    /*for(i<-1 to 908){
       //val hashstr = dataaccess.getBlockIdxByHeight(i)
       //println("height="+i+"\tblockhash="+hashstr.getBlockHash()+"\tprevioushash="+hashstr.getBlockPrevHash())
    }*/
  }
  
  def getChainInfo(sysname:String){
    val dataaccess :ImpDataAccess = ImpDataAccess.GetDataAccess(sysname)
    val info = dataaccess.getBlockChainInfo()
    println(info.toString())
  }
  
  def getblock(sysname:String,h:Long):Block={
    val dataaccess :ImpDataAccess = ImpDataAccess.GetDataAccess(sysname)
    dataaccess.getBlock4ObjectByHeight(h)
  }
  
  def blocker(nodes: Array[String], position:Int): String = {
    if(nodes.nonEmpty){
      var pos = position
      if(position >= nodes.size){
        pos = position % nodes.size
      }
      nodes(pos)
    }else{
      null
    }
  }
  
  case class randomNumber(var number:Long,var generateSerial:Int)
  private def getRandomList(seed:Long,candidatorLen:Int,candidatorTotal:Int):Array[randomNumber]={
    val m = 2*2*2*2*2*2*2*2*2*2*2*2*2*2*2*2*2*2*2*2
    val a = 2045
    val b = 1
    var randomArray = new Array[randomNumber](candidatorTotal)
    var hashSeed = seed.abs
    for(i<-0 to candidatorTotal-1){
      var tmpSeed = (a * hashSeed + b) % m
      tmpSeed = tmpSeed.abs
      if(tmpSeed == hashSeed) tmpSeed = tmpSeed + 1
      hashSeed = tmpSeed
      var randomobj = new randomNumber(hashSeed,i)
      randomArray(i) = randomobj
    }
    
    randomArray = randomArray.sortWith(
        (randomNumber_left,randomNumber_right)=> randomNumber_left.number < randomNumber_right.number)
        
    randomArray
  }
  
  def candidators(nodes: Set[String], seed: Array[Byte]): Array[String] = {
    var nodesSeq = nodes.toSeq.sortBy(f=>(f.toString()))
    var len = nodes.size / 2 + 1
    val min_len = 4
    len = if(len<min_len){
      if(nodes.size < min_len) nodes.size
      else min_len
    }
    else len
    if(len<4){
      null
    }
    else{
      var candidate = new Array[String](len)
      var hashSeed:Long = pathUtil.bytesToInt(seed)
      var randomList = getRandomList(hashSeed,len,nodes.size)
      //PrintRandomArray(randomList)
      //println(randomList(0).generateSerial)
      for(j<-0 to len-1){
        var e = randomList(j)
        candidate(j) = nodesSeq(e.generateSerial)
      }
      candidate
    }
  }
  
  def getStableNodes :Set[String] = {
    var source = Set.empty[String]
    try{
      /*source += AddressFromURIString.apply("akka.ssl.tcp://Repchain@10.10.10.92:8082")
      source += AddressFromURIString.apply("akka.ssl.tcp://Repchain@10.10.10.87:8082")
       source += AddressFromURIString.apply("akka.ssl.tcp://Repchain@10.10.10.67:8082")
      source += AddressFromURIString.apply("akka.ssl.tcp://Repchain@10.10.10.65:8082")*/
      //source += AddressFromURIString.apply("akka.ssl.tcp://Repchain@10.10.10.89:8082")
      //source += AddressFromURIString.apply("akka.ssl.tcp://Repchain@10.10.10.50:8082")
      
      
      val a1 = "1"//AddressFromURIString.apply("akka.ssl.tcp://Repchain@10.10.10.67:8082")
      val a2 = "2"//AddressFromURIString.apply("akka.ssl.tcp://Repchain@10.10.10.92:8082")
      
     
      val a3 = "3"//AddressFromURIString.apply("akka.ssl.tcp://Repchain@10.10.10.65:8082")
      val a4 = "4"//AddressFromURIString.apply("akka.ssl.tcp://Repchain@10.10.10.87:8082")
      
      //for(i<-4 to 1 by -1){
        val j = (new java.util.Random).nextInt(4) 
        j match {
          case 0 => 
            source += a1
            source += a2
            source += a3
            source += a4
          case 1=> 
            source +=  a2
            source += a3
            source += a4
            source += a1
          case 2=> 
            source += a3
            source += a4
            source += a1
            source += a2
          case 3=> 
            source += a4  
            source += a1
            source += a2
            source += a3
        }
      //}
      
      //source += AddressFromURIString.apply("akka.ssl.tcp://Repchain@10.10.10.89:8082")
      //source += AddressFromURIString.apply("akka.ssl.tcp://Repchain@10.10.10.50:8082")
    }finally{
      
    }
    source
  }
  
  def comparevote(hashvalue:String,pos:Int)={
    val c = getStableNodes 
    val cs = candidators(c, ShaDigest.hash(hashvalue))
    val blo = blocker(cs, pos)
    
     val c1 = getStableNodes 
    val cs1 = candidators(c1, ShaDigest.hash(hashvalue))
    val blo1 = blocker(cs1, pos)
    
    if(blo.toString() == blo1.toString()){
      println("========"+blo.toString())
    }else{
      println("<><><><>"+blo.toString() +"\t"+blo1.toString())
      throw new Exception("not equal")
    }
    
  }
  
  def printArray(in:Array[String])={
    print("-----------")
    if(in != null){
      in.foreach(f=>{
            print(f+",")
          }
        )
    }
  }
  
  def printvoteresult(hashvalue:String,pos:Int){
    var source = Set.empty[String]
    source += "1"  
    source += "2"
    source += "3"
    source += "4"
    source += "5"
    val cs = candidators(source, ShaDigest.hash(hashvalue))
    val blo = blocker(cs, pos)
    println("========"+blo.toString())
    printArray(cs)
  }
  
  def checkblker(blockhash:String,pos:Int)={
    val c = getStableNodes 
    val cs = candidators(c, ShaDigest.hash(blockhash))
    val blo = blocker(cs, pos)
    println(cs.toString())
    println("blker="+blo.toString())
  }
  
  def getBlockHeightForHash(sysname:String,hash:String):Long={
    val dataaccess :ImpDataAccess = ImpDataAccess.GetDataAccess(sysname)
    dataaccess.getBlockHeightByHash(hash)
  }
  
  def getBlockHashForHeight(sysname:String,h:Long):String={
    val dataaccess :ImpDataAccess = ImpDataAccess.GetDataAccess(sysname)
    dataaccess.getBlockHashByHeight(h)
  }
  
  def compareBlockContent(sysname1:String,sysname2:String,h:Long):Boolean={
    var b = false
    val dataaccess1 :ImpDataAccess = ImpDataAccess.GetDataAccess(sysname1)
    val dataaccess2 :ImpDataAccess = ImpDataAccess.GetDataAccess(sysname2)
    val blockarray1 = dataaccess1.getBlockByHeight(h)
    val blockarray2 = dataaccess2.getBlockByHeight(h)
    if(blockarray1.length == blockarray2.length){
      println("block array length equal")
    }
    
    val block1 = dataaccess1.getBlock4ObjectByHeight(h)
    val block2 = dataaccess2.getBlock4ObjectByHeight(h)
    
    if(block1.toByteString == block2.toByteString){
      println("block string length equal")
    }
    
    val rbb1 = block1.toByteArray
    val blockHash1 = ShaDigest.hashstr(rbb1);
    
    val rbb2 = block2.toByteArray
    val blockHash2 = ShaDigest.hashstr(rbb2);
    
    if(blockHash1 == blockHash2){
      println("block hash length equal")
    }
    println("block1 hash ="+blockHash1)
    println("block2 hash ="+blockHash2)
    
    b
  }
  
  def testop1={
    val dataaccess :ImpDataAccess = ImpDataAccess.GetDataAccess("1")
    dataaccess.printlnHashMap(dataaccess.FindByLike("c"))
    println(dataaccess.GetComputeMerkle4String)
    
    /*dataaccess.Put("c_sdfs_a", "a".getBytes)
    dataaccess.Put("c_sdfs_a1", "b".getBytes)
    dataaccess.Put("c_sdfs_a2", "c".getBytes)
    dataaccess.Put("a3", "d".getBytes)*/
    
    
    
    val preload :ImpDataPreload = ImpDataPreloadMgr.GetImpDataPreload("1","lllll")
    preload.Put("c_sdfs_a", "1".getBytes)
    preload.Put("c_sdfs_a1", "2".getBytes)
    preload.Put("c_sdfs_a2", "3".getBytes)
    println(preload.GetComputeMerkle4String)
    println(dataaccess.GetComputeMerkle4String)
    
    val preload1 :ImpDataPreload = ImpDataPreloadMgr.GetImpDataPreload("1","lllll")
    preload1.Put("c_sdfs_a", "1".getBytes)
    preload1.Put("c_sdfs_a1", "2".getBytes)
    preload1.Put("c_sdfs_a2", "3".getBytes)
    println(preload1.GetComputeMerkle4String)
    println(dataaccess.GetComputeMerkle4String)
    
    
    ImpDataPreloadMgr.Free("1","lllll")
  }
  
  def getTrans(sysname:String,h:Long):Set[String]={
    var txids = Set.empty[String]
    val dataaccess :ImpDataAccess = ImpDataAccess.GetDataAccess(sysname)
    for(i<-2 to h.asInstanceOf[Int]){
      val b = dataaccess.getBlock4ObjectByHeight(i.asInstanceOf[Long])
      val trans = b.transactions
      trans.foreach(f=>{
        txids += f.txid
        //println(f.txid)
      })
    }
    txids
  }
  
  def checkTrans(sysname:String,h:Long)={
    val txids = getTrans(sysname,h)
    println(txids.size)
    /*txids.toSeq.sortBy(f=>f.toString())
    txids.foreach(f=>{
      findTrans(txids,f)
    })*/
    
  }
  
  def findTrans(src:Set[String],txid:String)={
    var counter = 0
    src.foreach(f=>{
      if(txid == f){
        counter += 1
      }
    })
    if(counter > 1){
      println("repeat txid="+txid+"\tcounter="+counter)
    }else{
      println(txid)
    }
  }
  
  def main(args: Array[String]): Unit = {
    printvoteresult("431c6e619822d2e1e4c47eb3e402b309b22e525bbc7153d10503f6d7d2f56c7f",0)
    //testop1
    //getblockinfo
    
    /*checkblker("f145179eb76d066bfcf4c52fc3f54f890ab5e46a6a00db66ceb2a5e506352f49")
    checkblker("bee1eefa81a62f74a67b8b17e0b3ba41723713b565dbeb317f919787e9447fd8")
    checkblker("aede9528ce847ca31e9942aa83db1bdd5d3306919fd7ad81ca357ee1f28900f8")
    checkblker("da0a6b27ee602493c77caf85855eb401048d19012089651aef71edc21c85d2ed")*/
    /*for(i <- 1 to 1000){
      var blc = BlockHelper.createPreBlock("iwuroworwokdjfksdhfks", Seq())
      println(blc.hashCode())
    }*/
   //checkblker("fb8d62604039ac7bc5cbce4d5335a00ae1a64dc15df7edb36b394c95c4a04d11",0)
     /*checkblker("f145179eb76d066bfcf4c52fc3f54f890ab5e46a6a00db66ceb2a5e506352f49",1)
    checkblker("f145179eb76d066bfcf4c52fc3f54f890ab5e46a6a00db66ceb2a5e506352f49",2)
    checkblker("f145179eb76d066bfcf4c52fc3f54f890ab5e46a6a00db66ceb2a5e506352f49",3)
    checkblker("f145179eb76d066bfcf4c52fc3f54f890ab5e46a6a00db66ceb2a5e506352f49",4)*/
    //val a = getStableNodes
    //println(a.toString())
    /*for(i<-0 to 10){
      comparevote("e144290f27c20feb164c6919bdbfd72e94631afb7f3fb0a342b931a69195e2c2",1)
    }*/
    /*var l1 = 879l
    var l2 = 8090980989890080843l
    val b1 = l1.toByte
    val b2 = l2.toByte
    val l21 = b1.toLong
    val l22 = b2.toLong
    println(s"l21=${l21},l22=${l22}")*/
    
    /*val b232360 = getblock("2",109)
    println("b232360="+b232360.toString())
    
    val b132360 = getblock("1",109)
    println("b132360="+b132360.toString())
    
    
    val b332360 = getblock("3",109)
    println("b332360="+b332360.toString())
    
    val b432360 = getblock("4",109)
    println("b432360="+b432360.toString())
    
    val b532360 = getblock("5",109)
    println("b532360="+b532360.toString())*/
    
    /*val b232360 = getblock("2",33063)
    println("b232360="+b232360.toString())
    
    val b132360 = getblock("1",33064)
    println("b132360="+b132360.toString())
    
    
    val b332360 = getblock("3",33064)
    println("b332360="+b332360.toString())
    
    val b432360 = getblock("4",33064)
    println("b432360="+b432360.toString())*/
    
    
   // val b232360 = getblock("1",10663)
   // println("b232360="+b232360.toString())
    
    /*val b132360 = getblock("2",10663)
    println("b132360="+b132360.toString())
    
    
    val b332360 = getblock("3",10663)
    println("b332360="+b332360.toString())
    
    val b432360 = getblock("4",10663)
    println("b432360="+b432360.toString())*/
    
    //compareBlockContent("1","4",10663)
    
    
    
    /*getChainInfo("1")
    getChainInfo("2")
    getChainInfo("3")
    getChainInfo("4")
    getChainInfo("5")*/
    
    /*val b1132360 = getblock("1",1179334)
    println("b132360="+b1132360.toString())
    
    val b232360 = getblock("2",1179334)
    println("b232360="+b232360.toString())
    
    
    val b332360 = getblock("3",1179334)
    println("b332360="+b332360.toString())
    
    val b432360 = getblock("4",1179334)
    println("b432360="+b432360.toString())*/
    
    
    /*val b1132360 = getblock("1",1179433)
    println("b132360="+b1132360.toString())
    
    val b232360 = getblock("2",1179433)
    println("b232360="+b232360.toString())
    
    
    val b332360 = getblock("3",1179433)
    println("b332360="+b332360.toString())
    
    val b432360 = getblock("4",1179433)
    println("b432360="+b432360.toString())*/
    
    
    
    /*println(getBlockHeightForHash("1","cd50811c7fce9464f9fb703a4dbe49a14681ad4fdd39422e26af64b27821fb4e"))
    println(getBlockHeightForHash("2","b28fdda55e742bcb15afa33d13caadb180ed2e34a1fd097059c023984c38e520"))
    println(getBlockHeightForHash("3","cd50811c7fce9464f9fb703a4dbe49a14681ad4fdd39422e26af64b27821fb4e"))
    println(getBlockHeightForHash("4","cd50811c7fce9464f9fb703a4dbe49a14681ad4fdd39422e26af64b27821fb4e"))
    
    println(getBlockHashForHeight("1",8739))
    println(getBlockHashForHeight("2",8739))
    println(getBlockHashForHeight("3",8739))
    println(getBlockHashForHeight("4",8739))
    
    println(getBlockHashForHeight("1",8738))
    println(getBlockHashForHeight("2",8738))
    println(getBlockHashForHeight("3",8738))
    println(getBlockHashForHeight("4",8738))
    
    checkblker("e144290f27c20feb164c6919bdbfd72e94631afb7f3fb0a342b931a69195e2c2",0)*/
    //checkTrans("1",20328)
    
    /*val fis = new java.io.File("jks")
    if(fis.isDirectory()){
      val fs = fis.listFiles()
      var a = new scala.collection.mutable.ArrayBuffer[String]()
      for(fn<-fs){
        if(fn.isFile()){
          val fname = fn.getName
          val pos = fname.indexOf("mykeystore_")
          if(pos >= 0){
            a += fname.substring(pos+11, fname.indexOf(".jks"))
          }
        }
      }
      println(a.toString())
    }*/
    
    //39da02aeb7bd0f7900db9dba31bcdee439685003fcaa8df69158f3db1be1a01b
    
    
  }
  
}