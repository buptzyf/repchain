package rep.storage.test

import rep.proto.rc2.Block
import rep.storage.ImpDataAccess

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

object testBigKey extends App {
  val da2 = ImpDataAccess.GetDataAccess("12110107bi45jh675g.node2")
  val da1 = ImpDataAccess.GetDataAccess("121000005l35120456.node1")
  val da3 = ImpDataAccess.GetDataAccess("122000002n00123567.node3")
  val da4 = ImpDataAccess.GetDataAccess("921000005k36123789.node4")
  val da5 = ImpDataAccess.GetDataAccess("921000006e0012v696.node5")

  var txids = new ArrayBuffer[String]()
  var keys = new ArrayBuffer[String]()
  var blockheights : Array[Long] = Array[Long](2l,1000l,10000l,500000l,1000000l,2000000l,3000000,3500000,4000000)

  def gettxids(h:Long):Block={
    val start = System.currentTimeMillis()
    val blk = da1.getBlock4ObjectByHeight(h)
    val end = System.currentTimeMillis()
    println(s"read block ,h=${h},time=${end - start}")
    blk
  }

  def getAllTxidOfBlock(h:Long)={

      val blk = gettxids(h)
      if(blk != null){
        blk.transactions.foreach(t=>{
          txids += t.id
        })

        blk.statesSet.keys.foreach(k=>{
          keys += k
        })
      }

  }

  //getAllTxidOfBlock

  def getTx(txid:String)={
    val start = System.currentTimeMillis()
    val tx = da1.getTransDataByTxId(txid)
    val end = System.currentTimeMillis()
    println(s"read trans ,txid=${txid},time=${end - start}")
  }

  def getAllTrans={
    txids.foreach( txid =>{
      getTx(txid)
    })
  }

  //getAllTrans

  def getKey(key:String)={
    val start = System.currentTimeMillis()
    val v = da1.Get(key)
    val end = System.currentTimeMillis()
    println(s"read key ,key=${key},time=${end - start}")
  }

  def getAllKey={
    keys.foreach( key =>{
      getKey(key)
      //findKey(key)
    })
  }

  getAllKey

  def getChaininfo(d:ImpDataAccess)={
    val start = System.currentTimeMillis()
    val v = d.getBlockChainInfo()
    val end = System.currentTimeMillis()
    println(s"read chaininfo ,name=${d.getSystemName},time=${end - start}")
  }

  getChaininfo(da1)
  getChaininfo(da2)
  getChaininfo(da3)
  getChaininfo(da4)
  getChaininfo(da5)


  def findKey(key:String)={
    val start = System.currentTimeMillis()
    val v = da1.FindByLike(key)
    val end = System.currentTimeMillis()
    println(s"Find key ,key=${key},time=${end - start}")
  }



  def testRead={
    val readblocknumber = new ArrayBuffer[Long]()
    val start = System.currentTimeMillis()
    var i = 0
    val limit = 3171132
    for( i <- 1 to 10000){
      val h = Random.nextInt(limit)
      if(h < 3171132 && h > 0){
        getAllTxidOfBlock(h)
      }
    }
    val end = System.currentTimeMillis()
    println(s"read block:spent times=${(end-start)}ms")
    /*var tr = new testRateOfLevelDB(da1)

    tr.readAndWrite(keys.toArray)
    tr.readAndWrite(txids.toArray)*/

  }

  testRead
}
