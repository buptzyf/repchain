package rep.storage.test

import rep.storage.ImpDataAccess
import org.json4s.{ DefaultFormats, jackson }
import org.json4s.native.Serialization.{ write, writePretty }
import rep.protos.peer.CertId
import rep.protos.peer.Signature
import java.util.Date

import rep.crypto.Sha256
import scala.collection.mutable
import rep.storage.util.pathUtil
import scala.math._

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import scala.collection.immutable._

import java.nio.ByteBuffer
import rep.storage.util.pathUtil

import scalapb.json4s.JsonFormat
import org.json4s.{DefaultFormats, Formats, jackson}
import org.json4s.jackson.JsonMethods._
import org.json4s.DefaultFormats._

object blockDataCheck extends App {
  implicit val serialization = jackson.Serialization
  implicit val formats = DefaultFormats
  val da2 = ImpDataAccess.GetDataAccess("12110107bi45jh675g.node2")
  val da1 = ImpDataAccess.GetDataAccess("121000005l35120456.node1")
  val da3 = ImpDataAccess.GetDataAccess("122000002n00123567.node3")
  val da4 = ImpDataAccess.GetDataAccess("921000005k36123789.node4")
  val da5 = ImpDataAccess.GetDataAccess("921000006e0012v696.node5")

  val ch = 2
  val ch1 = 1 //308652l

  /*printlnBlock
  printlnBlocker
  printlnBlockHash

  getblockerForheight(da1,ch1)
  getblockerForheight(da2,ch1)
  getblockerForheight(da3,ch1)
  getblockerForheight(da4,ch1)
  getblockerForheight(da5,ch1)*/

  //fileOp
  case class bcinfo(height:Long,hash:String)
  case class resInfo (req:bcinfo,reqer:String,last:bcinfo)
  
  
  
  
  def longToByte(number:Long):Array[Byte]={
    var buffer = ByteBuffer.allocate(8)
    buffer.putLong(0, number)
    buffer.array()
  }
  
  def byteToLong(b:Array[Byte]):Long={
    var buffer = ByteBuffer.allocate(8)
    buffer.put(b, 0, b.length) 
    buffer.flip()
    buffer.getLong()
  }
  
  /*val start = System.currentTimeMillis()
  for(i<-0 to 100000){
  val mylong : Long = 294723843
  val b = longToByte(mylong)
  //val l = byteToLong(b)
  //println(s"old=${mylong},new=${l}")
  }
  val end = System.currentTimeMillis()
  println("spent time="+(end-start))
  
  val start1 = System.currentTimeMillis()
  for(i<-0 to 100000){
  val mylong : Long = 294723843
  val b = pathUtil.longToByte(mylong)
  //val l = pathUtil.byteToLong(b)
  //println(s"old=${mylong},new=${l}")
  }
  val end1 = System.currentTimeMillis()
  println("spent time="+(end1-start1))*/
  
 /* def  longToByte(number:Long) :Array[Byte]={
      var param = number
	    var b = new Array[Byte](8)
	    for (i <- 7 to 0 by -1) {
	      b(i) = (param % 256).asInstanceOf[Byte]
	      param = param >> 8
	    }
	    b
	}
	
	def byteToLong(b : Array[Byte]) : Long = {
	    return ((( b[0].asInstanceOf[Long] & 0xff) << 56) | (((long) b[1] & 0xff) << 48) | (((long) b[2] & 0xff) << 40) | (((long) b[3] & 0xff) << 32) | (((long) b[4] & 0xff) << 24)
	        | (((long) b[5] & 0xff) << 16) | (((long) b[6] & 0xff) << 8) | ((long) b[7] & 0xff));
	}*/
  
  /*var sets = new Array[resInfo](5)
  sets(0)  = resInfo(bcinfo(10,"hash10"),"certnode1",bcinfo(8,"hash8"))
  sets(1)  = resInfo(bcinfo(9,"hash9"),"certnode1",bcinfo(8,"hash8"))
  sets(2)  = resInfo(bcinfo(10,"hash10"),"certnode1",bcinfo(8,"hashxx"))
  sets(3)  = resInfo(bcinfo(11,"hash11"),"certnode1",bcinfo(8,"hash8"))
  sets(4)  = resInfo(bcinfo(8,"hash8"),"certnode1",bcinfo(8,"hashyy"))
  
  println(sets.groupBy(x => x.req.hash).map(x => (x._1,x._2.length)).toList.sortBy(x => -x._2).mkString(","))
  println(sets.groupBy(x => x.last.hash).map(x => (x._1,x._2.length)).toList.sortBy(x => -x._2).mkString(","))
  val tmp = sets.groupBy(x => x.req.height).map(x => (x._1,x._2.length)).toList.sortBy(x => -x._2)
  println(tmp.mkString(","))
  val t = tmp(0)._1
  val fsets = sets.filter(_.req.height == t)
  println(fsets.mkString(","))*/
  
  def fileOp={
    val filename = "/Users/jiangbuyun/tmp.fos"
    val a  = new Array[String](1000)
    for(i<-0 to 1000-1){
      a(i) = i.toString()
    }
    val astr = a.mkString(",")
    println(astr)
    writefile(filename, astr.getBytes)
    delTailOfFile(filename, 10)
    delTailOfFile(filename, 10)
    delTailOfFile(filename, 10)
    delTailOfFile(filename, 10)
  }
  
  def writefile(fn: String, bb: Array[Byte]): Boolean = {
    var bv = false
    var rf: RandomAccessFile = null;
    var channel: FileChannel = null;
    try {
      rf = new RandomAccessFile(fn, "rw")
      channel = rf.getChannel()
      channel.position(0)
      var buf = ByteBuffer.wrap(bb)
      channel.write(buf)
      bv = true
    } catch {
      case e: Exception =>
        e.printStackTrace();
        throw e;
    } finally {
      if (channel != null) {
        try {
          channel.close();
        } catch {
          case ec: Exception =>
            ec.printStackTrace();
        }
      }

      if (rf != null) {
        try {
          rf.close();
        } catch {
          case er: Exception =>
            er.printStackTrace();
        }
      }
    }
    bv
  }

  def delTailOfFile(fn: String, dellength: Long):Boolean = {
  var bv = false
    var rf: RandomAccessFile = null;
    var channel: FileChannel = null;
    try {
      rf = new RandomAccessFile(fn, "rw")
      channel = rf.getChannel()
      val l = channel.size() - dellength
      channel.truncate(l)
      
      val readlength : Int = l.toInt//(channel.size() - dellength).toInt
      channel.position(0);
			val	 buf = ByteBuffer.allocate(readlength);   
				channel.read(buf);
				buf.flip();
				val rb = buf.array();
				println(new String(rb))
				
      bv = true
    } catch {
      case e: Exception =>
        e.printStackTrace();
        throw e;
    } finally {
      if (channel != null) {
        try {
          channel.close();
        } catch {
          case ec: Exception =>
            ec.printStackTrace();
        }
      }

      if (rf != null) {
        try {
          rf.close();
        } catch {
          case er: Exception =>
            er.printStackTrace();
        }
      }
    }
    bv
  }

  def printlnBlocker = {
    println(s"height=$ch,systemname=${da1.SystemName},${findEndorseTime(da1, ch)}")
    println(s"height=$ch,systemname=${da2.SystemName},${findEndorseTime(da2, ch)}")
    println(s"height=$ch,systemname=${da3.SystemName},${findEndorseTime(da3, ch)}")
    println(s"height=$ch,systemname=${da4.SystemName},${findEndorseTime(da4, ch)}")
    println(s"height=$ch,systemname=${da5.SystemName},${findEndorseTime(da5, ch)}")
  }

  def printlnBlock = {
    println(s"height=$ch,systemname=${da1.SystemName},${readBlockToString(da1, ch)}")
    println(s"height=$ch,systemname=${da2.SystemName},${readBlockToString(da2, ch)}")
    println(s"height=$ch,systemname=${da3.SystemName},${readBlockToString(da3, ch)}")
    println(s"height=$ch,systemname=${da4.SystemName},${readBlockToString(da4, ch)}")
    println(s"height=$ch,systemname=${da5.SystemName},${readBlockToString(da5, ch)}")
  }

  def printlnBlockHash = {
    readBlockHash(da1, ch)
    readBlockHash(da2, ch)
    readBlockHash(da3, ch)
    readBlockHash(da4, ch)
    readBlockHash(da5, ch)
  }
  def readBlockHash(da: ImpDataAccess, h: Long) = {
    for (i <- 0 to 9) {
      val b = da.getBlock4ObjectByHeight(h - i)
      println(s"hash---systemname=${da.SystemName},,height=${(h - i)},hashcode=${b.hashOfBlock.toStringUtf8()}")
    }
  }

  def readBlockToString(da: ImpDataAccess, h: Long): String = {
    val b = da.getBlock4ObjectByHeight(h)
     val r = JsonFormat.toJson(b)   
    pretty(render(r))
    //writePretty(b.endorsements)
  }
  
  /*println(readBlockToString(da4,1))
  println(readBlockToString(da4,2))
  println(readBlockToString(da4,3))*/
  
  /*da5.FindByLike("rechain_", 1).foreach(f=>{
    println(f._1)
    println(new String(f._2))
  })*/

  def findEndorseTime(da: ImpDataAccess, h: Long): String = {
    val b = da.getBlock4ObjectByHeight(h)
    val endors = b.endorsements
    var mintime = Long.MaxValue
    var certid: CertId = null
    var eidx: Signature = null
    endors.foreach(e => {
      if (e.tmLocal.get.seconds + e.tmLocal.get.nanos < mintime) {
        mintime = e.tmLocal.get.seconds + e.tmLocal.get.nanos
        certid = e.certId.get
        eidx = e
      }
    })
    if (certid != null) {
      writePretty(certid) + ",time=" + mintime + ",seconds=" + eidx.tmLocal.get.seconds + ",nanos=" + eidx.tmLocal.get.nanos
    } else {
      ""
    }
  }

  var l : Long = 119649
  for(i<-0 to 10){
    
    getblockerForheight(da1, l)
    l = l + 1
  }
  
  def getblockerForheight(da: ImpDataAccess, h: Long) = {
    var nodes = new Array[String](5)
    nodes(0) = "12110107bi45jh675g.node2"
    nodes(1) = "121000005l35120456.node1"
    nodes(2) = "122000002n00123567.node3"
    nodes(3) = "921000005k36123789.node4"
    nodes(4) = "921000006e0012v696.node5"
    val b = da.getBlock4ObjectByHeight(h)

    val candidatorCur = candidators(da.getSystemName, b.hashOfBlock.toStringUtf8(), nodes.toSet, Sha256.hash(b.hashOfBlock.toStringUtf8()))
    println(s"height=$h,systemname=${da.SystemName},${candidatorCur.mkString("|")}")
  }

  ///////////////////////////////////////vote code/////////////////////////////////////////////////
  case class randomNumber(var number: Long, var generateSerial: Int)

  def blocker(nodes: Array[String], position: Int): String = {
    if (nodes.nonEmpty) {
      var pos = position
      if (position >= nodes.size) {
        pos = position % nodes.size
      }
      nodes(pos)
    } else {
      null
    }
  }

  private def getRandomList(seed: Long, candidatorLen: Int, candidatorTotal: Int): Array[randomNumber] = {
    val m = pow(2, 20).toLong
    val a = 2045
    val b = 1
    var randomArray = new Array[randomNumber](candidatorTotal)
    var hashSeed = seed.abs
    for (i <- 0 to candidatorTotal - 1) {
      var tmpSeed = (a * hashSeed + b) % m
      tmpSeed = tmpSeed.abs
      if (tmpSeed == hashSeed) tmpSeed = tmpSeed + 1
      hashSeed = tmpSeed
      var randomobj = new randomNumber(hashSeed, i)
      randomArray(i) = randomobj
    }

    randomArray = randomArray.sortWith(
      (randomNumber_left, randomNumber_right) => randomNumber_left.number < randomNumber_right.number)

    randomArray
  }

  def candidators(Systemname: String, hash: String, nodes: Set[String], seed: Array[Byte]): Array[String] = {
    var nodesSeq = nodes.toSeq.sortBy(f => (f.toString()))
    var len = nodes.size / 2 + 1
    val min_len = 4
    len = if (len < min_len) {
      if (nodes.size < min_len) nodes.size
      else min_len
    } else len
    if (len < 4) {
      null
    } else {
      var candidate = new Array[String](len)
      var hashSeed: Long = pathUtil.bytesToInt(seed)
      var randomList = getRandomList(hashSeed, len, nodes.size)
      //println(randomList(0).generateSerial)
      //println(randomList.mkString(","))
      for (j <- 0 to len - 1) {
        var e = randomList(j)
        candidate(j) = nodesSeq(e.generateSerial)
      }
      //println(s"sysname=${Systemname},hash=${hash},hashvalue=${hashSeed},randomList=${randomList.mkString("|")}")
      //println( s"sysname=${Systemname},candidates=${candidate.mkString("|")}")

      candidate
    }
  }

}