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

package rep.storage.timeAnalysiser

import scala.collection.mutable
import rep.crypto.Sha256
import rep.storage.util.pathUtil

object testRandomVote {
  case class randomstatis(listsize:Int,clist:scala.collection.mutable.ArrayBuffer[String]) 
  case class randomNumber(var number:Long,var generateSerial:Int,var sortPos:Int)
  
  var statisvar:randomstatis = null
  var statis:Array[Int] = null
  
  
  def getRandomList(seed:Long,candidatorLen:Int,candidatorTotal:Int):Array[randomNumber]={
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
      var randomobj = new randomNumber(hashSeed,i,-1)
      randomArray(i) = randomobj
    }
    
    randomArray = randomArray.sortWith(
        (randomNumber_left,randomNumber_right)=> randomNumber_left.number < randomNumber_right.number)
        
    for(i<-0 to randomArray.size-1){
       randomArray(i).sortPos = i
    }
    
    randomArray = randomArray.sortWith(
        (randomNumber_left,randomNumber_right)=> randomNumber_left.generateSerial < randomNumber_right.generateSerial)
        
    randomArray
  }
  
  def  PrintRandomArray(ra : Array[randomNumber])={
    if(ra == null) println("no data,input is null")
    if(ra.size == 0) println("no data, input length is zero")
    for(i<-0 to (ra.length-1)){
      println("randomnumber="+ra(i).number+",generateSerial="+ra(i).generateSerial+",sortpos="+ra(i).sortPos+"|||")
    }
  }
  
    def getRandomString(length:Int):String={
     val str="abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
     var random=new util.Random()
     var sb=new StringBuffer()
     for( i<-0 to length-1){
       val number= random.nextInt(62)
       sb.append(str.charAt(number))
     }
     sb.toString()
   }
  
    def getRandomSha256String(length:Int): Array[Byte]={
      val s = getRandomString(length)
      //println(Sha256.hashstr(s))
      Sha256.hashToBytes(s)
    }
    
  def findIndex(indexs:Array[Int],idx:Int):Boolean={
    var b = false
    for( i<-0 to indexs.length-1){
      if(indexs(i) == idx){
        b = true
      }
    }
    b
  }
    
  def candidators(nodes: Set[String], seed: Array[Byte]): Array[String] = {
    var nodesSeq = nodes.toSeq.sortBy(f=>(Integer.parseInt(f.toString())))
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
      //var candidate = mutable.Seq.empty[String]
      var candidate = new Array[String](len)
      var hashSeed:Long = pathUtil.bytesToInt(seed)
      var randomList = getRandomList(hashSeed,len,nodes.size)
      //PrintRandomArray(randomList)
      println(randomList(0).sortPos)
      for(j<-0 to len-1){
        var e = randomList(j)
        //candidate = (candidate :+ nodesSeq(e.sortPos))
        candidate(j) = nodesSeq(e.sortPos)
        
      }
      //candidate.toSet
      candidate
    }
  }
  
  def blocker(nodes: Array[String], position:Int): String = {
    if(nodes.nonEmpty){
      var pos = position
      if(position >= nodes.size){
        pos = position % nodes.size
      }
      val a = nodes.toList
      nodes(pos)
    }else{
      null
    }
  }
  
  def getStableNodes(length:Int) :Set[String] = {
    var source = Set.empty[String]
    for(i <- 0 to length-1){
      source += i.toString()
    }
      
    source
  }
  
  
  
   def checkblker(nodecount:Int,pos:Int)={
    val c = getStableNodes(nodecount)
    val hashbytes = getRandomSha256String(500)
    val cs = candidators(c, hashbytes)
    
    val blo = blocker(cs, pos)
    //statisvar.clist += blo.toString()
    //println(cs.toString()+"\t"+blo.toString())
    val idx = Integer.parseInt(blo)
    statis(idx) = statis(idx) + 1
    //println(blo.toString())
  }
  
   def testRandomBlocker(count:Int,nodes:Int,pos:Int){
     //statisvar = new randomstatis(count,new scala.collection.mutable.ArrayBuffer[String](count))
     statis = new Array[Int](nodes)
     for(i<-0 to count-1){
       checkblker(nodes,pos)
     }
     //println(statisvar.clist.toString())
     for(j<-0 to statis.length-1) print(","+"blocker="+j+"("+statis(j).toString()+")")
   }
   
  def printRandom(size:Int){
    for(j<-0 to 1000){
      val istr = getRandomSha256String(500)
      var i = pathUtil.bytesToInt(istr)
      if(i < 0){
        i = i.abs
      }
      println("\t"+i % size)
    }
  }
  
  def testPrintRandomArray(len:Int,size:Int){
    for(j<-0 to 1000){
      val istr = getRandomSha256String(500)
      var seed = pathUtil.bytesToInt(istr)
      var ra = getRandomList(seed,len,size)
      PrintRandomArray(ra)
      println("-------------------")
    }
  }
  
  def printRandomlist(size:Int){
    val m = 2*2*2*2*2*2*2*2*2*2*2*2*2*2*2*2*2*2*2*2//43112609 
    val a = 2045//12477  // a = 4p + 1  3119
    val b = 1//4667  // b = 2q + 1  2333
    var tmpstatis = new Array[Int](size)
    val istr = getRandomSha256String(500)
    var l = pathUtil.bytesToInt(istr)
    if(l < 0){
      l = l.abs
    }
    
    for(i<-0 to 1000){
      l = a*l+b
      if(l < 0) l = l.abs
      tmpstatis(l % size) = tmpstatis(l % size) +1
      println("\t"+l % size)
    }
    
    for(j<-0 to tmpstatis.length-1)
        print(","+tmpstatis(j))
  }
  
  
  def main(args: Array[String]): Unit = {
    //println(pathUtil.IsPrime(2333))
    testRandomBlocker(100000,5,0)
    //printRandom(4)
    //printRandomlist(30)
    //testPrintRandomArray(16,30)
  }
}

