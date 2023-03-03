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

package rep.utils.sync

import org.scalatest.FunSuite
import org.scalatest.{Matchers, PropSpec}


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