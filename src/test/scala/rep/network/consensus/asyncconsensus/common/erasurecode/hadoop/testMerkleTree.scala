package rep.network.consensus.asyncconsensus.common.erasurecode.hadoop

import com.google.protobuf.ByteString
import rep.crypto.Sha256
import rep.network.consensus.asyncconsensus.common.merkle.MerkleTree

import scala.collection.mutable.ArrayBuffer

object testMerkleTree extends App {
  val data = new Array[ByteString](2000)
  for(i<-0 to 1999){
    data(i) = ByteString.copyFrom(java.util.UUID.randomUUID().toString,"UTF-8")
  }


  val start = System.currentTimeMillis()
  val tree = MerkleTree.createMerkleTree(data)
  val end = System.currentTimeMillis()
  System.out.println(s"ByteString time=${end-start}ms")


  val data1 = new Array[String](2000)
  for(i<-0 to 1999){
    data1(i) = java.util.UUID.randomUUID().toString
  }
  val start1 = System.currentTimeMillis()
  val tree1 = MerkleTree.createMerkleTreeWithString(data1)
  val end1 = System.currentTimeMillis()
  System.out.println(s"String time1=${end1-start1}ms")

  //连续执行j次，统计每次merkle树的建立时间
  for(j<-0 to 10){
    //每次重新生成2000个叶子
    val data2 = new ArrayBuffer[String]()
    for(i<-0 to 50000){
      data2.append(java.util.UUID.randomUUID().toString)
    }
    var start2 = System.currentTimeMillis()
    Sha256.hash( data2.toString().getBytes("UTF-8"))
    var end2 = System.currentTimeMillis()
    System.out.println(s"sha256(${j}),String, time=${end2-start2}ms")

    val bs = data2.toString().getBytes("UTF-8")
     start2 = System.currentTimeMillis()
    Sha256.hash( bs )
     end2 = System.currentTimeMillis()
    System.out.println(s"sha256(${j}),Bytes, time=${end2-start2}ms")
  }

  //连续执行j次，统计每次merkle树的建立时间
  /*for(j<-0 to 10){
    //每次重新生成2000个叶子
    val data2 = new Array[Array[Byte]](1000000)
    for(i<-0 to 999999){
      data2(i) = java.util.UUID.randomUUID().toString.getBytes("UTF-8")
    }
    //开始建立merkle树
    val start2 = System.currentTimeMillis()
    //建立merkle树的方法
    val tree2 = MerkleTree.createMerkleTreeWithBytes(data2)
    val end2 = System.currentTimeMillis()
    System.out.println(s"Bytes(${j}) time2=${end2-start2}ms")
  }*/



}
