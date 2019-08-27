package rep.storage.test

import rep.storage.ImpDataAccess
import rep.protos.peer._
import scalapb.json4s.JsonFormat
import org.json4s.{DefaultFormats, Formats, jackson}
import org.json4s.jackson.JsonMethods._
import org.json4s.DefaultFormats._
import java.nio.ByteBuffer
import java.nio.charset._
import java.nio.CharBuffer
import rep.utils.SerializeUtils

object checkTrans extends App {
  //通过节点名称来访问存储数据
  val da1 = ImpDataAccess.GetDataAccess("121000005l35120456.node1")
  var last = 1000 
  var lastheight = 2l
 
  /**
   * 
   * 该方法通过账号名称，获取账号的所有状态信息
   * */
  printlnAllBlockTrans("921000005k36123789")

  //从区块中分析对应账号的交易数据
  private def printlnTransValue(block:Block,height:Long,key:String)={
    block.transactionResults.foreach(f=>{
      var st = s"height=${height},txid=${f.txId},code=${f.getResult.code},"
      var valuelist = ""
     
      if(f.ol.length == 2){
        if(f.ol(0).key == key){
          val rl = f.ol(0)
          val o   = SerializeUtils.deserialise(rl.oldValue.toByteArray()).asInstanceOf[Int]
          val n = SerializeUtils.deserialise(rl.newValue.toByteArray()).asInstanceOf[Int]
          val d = n - o
          valuelist = s"~~~key=${rl.key},d=${n-o},old=${o},last=${n},isequal=${n == (last + d)},~~isequal=${this.lastheight}"
          last = n
          this.lastheight = height
        }else if(f.ol(1).key == key){
          val rl = f.ol(1)
          val o   = SerializeUtils.deserialise(rl.oldValue.toByteArray()).asInstanceOf[Int]
          val n = SerializeUtils.deserialise(rl.newValue.toByteArray()).asInstanceOf[Int]
          val d = n - o
          valuelist = s"~~~key=${rl.key},d=${n-o},old=${o},last=${n},isequal=${n == (last + d)},~~isequal=${this.lastheight}"
          last = n
          this.lastheight = height
        }
      }else{
        println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")
      }
   
      if(valuelist != ""){
        st = st + valuelist
        println(st)
      }
    })
  }

  
  def printlnAllBlockTrans(account:String)={
    val chaininfo = da1.getBlockChainInfo()
    val height = chaininfo.height
    var loop = 2
    while(loop <= height){
      val block = da1.getBlock4ObjectByHeight(loop)
      printlnTransValue(block:Block,loop,account)
      loop +=  1
    }
  }
  
}