package rep.network.consensus.asyncconsensus.common.merkle

import akka.util.Timeout
import com.google.protobuf.ByteString
import org.apache.commons.lang3.ArrayUtils
import rep.crypto.Sha256
import rep.protos.peer.DataOfStripe

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, Future}
import scala.math._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._



/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-02-13
 * @category	merkle Tree 相关的操作，包括建立merkle tree，提取merkletree的一个分支，验证一个merkletree的分支的正确性。
 */
object MerkleTree {
  private def log(m: Double, base: Double) = math.log(m) / math.log(base)



  private def initByteSringArray(data:Array[ByteString]):Array[ByteString]={
    for( i <- 0 to data.length-1){
      data(i) = ByteString.EMPTY
    }
    data
  }

  def createMerkleTree(data:Array[ByteString]):Array[ByteString]= {
    var r : Array[ByteString] = null
    if(data != null && data.length >= 1){
      val N = data.length
      val bottomrow = pow(2,ceil(log(N, 2))).toInt
      r = initByteSringArray(new Array[ByteString](2 * bottomrow))
      for( i <- 0 to N-1){
        r(bottomrow + i) = ByteString.copyFromUtf8(Sha256.hashstr(data(i).toStringUtf8))
      }
      for(i <- (1 until (bottomrow)).reverse){
        r(i) = ByteString.copyFromUtf8(Sha256.hashstr(r(i*2).toStringUtf8 + r(i * 2 +1).toStringUtf8))
      }
    }else{
      r
    }
    r
  }

  private def initSringArray(data:Array[String]):Array[String]={
    for( i <- 0 to data.length-1){
      data(i) = ""
    }
    data
  }

  def createMerkleTreeWithString(data:Array[String]):Array[String]= {
    var r : Array[String] = null
    if(data != null && data.length >= 1){
      val N = data.length
      val bottomrow = pow(2,ceil(log(N, 2))).toInt
      r = initSringArray(new Array[String](2 * bottomrow))
      for( i <- 0 to N-1){
        r(bottomrow + i) = Sha256.hashstr(data(i).getBytes("UTF-8"))
      }
      for(i <- (1 until (bottomrow)).reverse){
        r(i) = Sha256.hashstr((r(i*2) + r(i * 2 +1)).getBytes("UTF-8"))
      }
    }else{
      r
    }
    r
  }

  private def initBytesArray(data:Array[Array[Byte]]):Array[Array[Byte]]={
    (0 to data.length-1).foreach(idx=>{
      data(idx) = "".getBytes()
    })
    /*for( i <- 0 to data.length-1){
      data(i) = "".getBytes("UTF-8")
    }*/
    data
  }

  def createMerkleTreeWithBytes(data:Array[Array[Byte]]):Array[Array[Byte]]= {
    var r : Array[Array[Byte]] = null
    if(data != null && data.length >= 1){
      val N = data.length
      val bottomrow = pow(2,ceil(log(N, 2))).toInt

      //r = new Array[Array[Byte]](2 * bottomrow)

      r = Array.ofDim[Byte](2 * bottomrow,32)
      //r = initBytesArray(new Array[Array[Byte]](2 * bottomrow))
      //val start1 = System.currentTimeMillis()
      /*(0 to N-1).foreach(idx=>{
        r(bottomrow + idx) = Sha256.hash(data(idx))
        //System.out.println(s"r(${bottomrow + idx})=${r(bottomrow + idx).mkString("")}")
      })*/
      for( i <- 0 to N-1){
        r(bottomrow + i) = Sha256.hash(data(i))
      }
      //val end1 = System.currentTimeMillis()
      //System.out.println(s"once time=${end1-start1}ms")

      //val start = System.currentTimeMillis()
      for(idx <- (1 until (bottomrow)).reverse){
      //(1 until (bottomrow)).reverse.foreach(idx=>{
        /*val a = new Array[Byte](64)
        var first = 0
        if(r(idx*2) != null){
          System.arraycopy(r(idx*2),0,a,0,32)
          first = 32
        }
        if(r(idx*2 + 1) != null) {
          System.arraycopy(r(idx * 2 +1),0,a,first,32)
        }

        r(idx) = Sha256.hash(a)*/

        val a = new Array[Byte](64)

        System.arraycopy(r(idx*2),0,a,0,32)
        System.arraycopy(r(idx*2+1),0,a,32,32)
        //System.out.println(s"a(${idx*2})=${a.mkString("")}")
        r(idx) = Sha256.hash(a)

        /*val a = new Array[Byte](r(idx*2).length + r(idx * 2 +1).length)
        System.arraycopy(r(idx*2),0,a,0,r(idx*2).length)
        System.arraycopy(r(idx * 2 +1),0,a,r(idx*2).length,r(idx * 2 +1).length)
        r(idx) = Sha256.hash(a)*/
      }
      /*for(i <- (1 until (bottomrow)).reverse){
        val a = new Array[Byte](r(i*2).length + r(i * 2 +1).length)
        System.arraycopy(r(i*2),0,a,0,r(i*2).length)
        System.arraycopy(r(i * 2 +1),0,a,r(i*2).length,r(i * 2 +1).length)
        r(i) = Sha256.hash(a)
        //r(i) = Sha256.hash(r(i*2) ++ r(i * 2 +1))
        //r(i) = Sha256.hash(Array.concat(r(i*2),r(i * 2 +1)))
      }*/
      //val end = System.currentTimeMillis()
      //System.out.println(s"array time=${end-start}ms")
    }else{
      r
    }
    r
  }


  def getMerkleBranch(index:Int, mtree:Array[ByteString]):Seq[ByteString]= {
    var res = new ArrayBuffer[ByteString]()
    var t = index + (mtree.length >> 1)
    while(t > 1){
      res.append(mtree(t ^ 1))
      t = t / 2
    }
    res.toSeq
  }

  def merkleVerify(numOfNode:Int, data:DataOfStripe):Boolean= {
    var r = false
    val src:ByteString = data.stripe
    val roothash = data.rootHash
    val branch = data.branch
    val index = data.serial
    if(index >= 0 && index < numOfNode){
      if(branch.length == ceil(log(numOfNode, 2))){
        var tmp = ByteString.copyFromUtf8(Sha256.hashstr(src.toStringUtf8))
        var tindex = index
        for(br <- branch){
          if((tindex & 1 ) > 0){
            tmp = ByteString.copyFromUtf8(Sha256.hashstr(br.toStringUtf8 + tmp.toStringUtf8))
          }else{
            tmp = ByteString.copyFromUtf8(Sha256.hashstr(tmp.toStringUtf8 + br.toStringUtf8))
          }
          tindex = tindex >> 1
        }
        if(tmp.toStringUtf8 == roothash.toStringUtf8){
          r = true
        }
      }
    }
    r
  }
}
