package rep.utils.sync

import org.scalatest.FunSuite
import org.scalatest.{Matchers, PropSpec}
import rep.crypto._
import java.io._
import rep.network.util.NodeHelp
import scala.collection._
import scala.util.control.Breaks._
import rep.protos.peer._
import com.google.protobuf.ByteString


object SyncHelpSpec extends PropSpec
  //with PropertyChecks
  //with GeneratorDrivenPropertyChecks
  with Matchers {

  

  property("signed message should be verifiable with appropriate public key") {
    //forAll {
    //val list2 =  test_init_2
   /* var list2 = new immutable.TreeMap[String, SynchronizeRequester.ResponseInfo]()
    list2 += "node1" -> SynchronizeRequester.ResponseInfo(new BlockchainInfo(1l, 0L, ByteString.copyFromUtf8("currentBlockHash"), ByteString.EMPTY, ByteString.EMPTY), null)
    list2 += "node2" -> SynchronizeRequester.ResponseInfo(new BlockchainInfo(2l, 0L, ByteString.copyFromUtf8("currentBlockHash"), ByteString.EMPTY, ByteString.EMPTY), null)
    list2 += "node3" -> SynchronizeRequester.ResponseInfo(new BlockchainInfo(3l, 0L, ByteString.copyFromUtf8("currentBlockHash"), ByteString.EMPTY, ByteString.EMPTY), null)
    list2 += "node4" -> SynchronizeRequester.ResponseInfo(new BlockchainInfo(2l, 0L, ByteString.copyFromUtf8("currentBlockHash"), ByteString.EMPTY, ByteString.EMPTY), null)

    
    var r = SyncHelp.GetGreatMajorityHeight(list2, 0, 5)
    var h =  r.height should be (0)
    
     r = SyncHelp.GetGreatMajorityHeight(list2, 1, 5)
    r should be (null)
    
    
    //val list1 =  test_init_1
    var list1 = new immutable.TreeMap[String, SynchronizeRequester.ResponseInfo]()
    list1 += "node1" -> SynchronizeRequester.ResponseInfo(new BlockchainInfo(1l, 0L, ByteString.copyFromUtf8("currentBlockHash"), ByteString.EMPTY, ByteString.EMPTY), null)
    list1 += "node2" -> SynchronizeRequester.ResponseInfo(new BlockchainInfo(2l, 0L, ByteString.copyFromUtf8("currentBlockHash"), ByteString.EMPTY, ByteString.EMPTY), null)
    list1 += "node3" -> SynchronizeRequester.ResponseInfo(new BlockchainInfo(3l, 0L, ByteString.copyFromUtf8("currentBlockHash"), ByteString.EMPTY, ByteString.EMPTY), null)
    list1 += "node4" -> SynchronizeRequester.ResponseInfo(new BlockchainInfo(2l, 0L, ByteString.copyFromUtf8("currentBlockHash"), ByteString.EMPTY, ByteString.EMPTY), null)

     r = SyncHelp.GetGreatMajorityHeight(list1, 0, 5)
     r.height should be (1)
    
     r = SyncHelp.GetGreatMajorityHeight(list1, 1, 5)
    r.height should be (1)
    
     r = SyncHelp.GetGreatMajorityHeight(list1, 2, 5)
      r.height should be (2)
    
    r = SyncHelp.GetGreatMajorityHeight(list1, 3, 5)
     r should be (null)
    
    */
    //}
  }
  
  /*def main(args: Array[String]): Unit = {
    var ResultList = test_init_1
    val r = SyncHelp.GetGreatMajorityHeight(ResultList, 2, 5)
    if (r == null) {
      println("r is null")
    } else {
      println(s"addr:${r.addr},height:${r.height},count=${r.count}")
    }
  }*/
}