package rep.network.consensus.asyncconsensus.common.merkle

import com.google.protobuf.ByteString
import rep.crypto.Sha256
import rep.protos.peer.DataOfStripe

import scala.collection.mutable.ArrayBuffer
import scala.math._

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
