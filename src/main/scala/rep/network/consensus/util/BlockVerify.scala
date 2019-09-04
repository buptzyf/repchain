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

package rep.network.consensus.util

import rep.protos.peer._
import rep.crypto.cert.SignTool
import scala.util.control.Breaks._
import rep.crypto.Sha256
import com.google.protobuf.ByteString
import rep.utils.IdTool

object BlockVerify {
/****************************交易验证签名相关的操作开始**********************************************************/
  def VerifyOneSignOfTrans(t: Transaction, sysName: String): (Boolean, String) = {
    var result = false
    var resultMsg = ""

    try {
      val sig = t.signature
      val tOutSig = t.clearSignature
      result = SignTool.verify(sig.get.signature.toByteArray(), tOutSig.toByteArray, sig.get.getCertId, sysName)
    } catch {
      case e: RuntimeException =>
        result = false
        resultMsg = s"The transaction(${t.id}) is not trusted${e.getMessage}"
    }
    (result, resultMsg)
  }

  def VerifySignOfMultiTrans(trans: Seq[Transaction], sysName: String): (Boolean, String) = {
    var result = true
    var resultMsg = ""
    try {
      breakable(
        trans.foreach(f => {
          val r = VerifyOneSignOfTrans(f, sysName)
          if (!r._1) {
            result = false
            resultMsg = r._2
            break
          }
        }))
    } catch {
      case e: RuntimeException =>
        result = false
        resultMsg = s"The VerifySignOfMultiTrans error,info=${e.getMessage}"
    }
    (result, resultMsg)
  }

  def VerifyAllTransSignOfBlock(block: Block, sysName: String): (Boolean, String) = {
    try {
      VerifySignOfMultiTrans(block.transactions.toArray[Transaction], sysName)
    } catch {
      case e: RuntimeException => (false, s"Transaction's sign Error,info=${e.getMessage}")
    }
  }
/****************************交易验证签名相关的操作结束**********************************************************/

/****************************区块验证签名相关的操作开始**********************************************************/
  def VerifyOneEndorseOfBlock(endor: Signature, NonSignDataOfBlock: Array[Byte], sysName: String): (Boolean, String) = {
    var result = false
    var resultMsg = ""
    try {
      val certid = endor.getCertId
      result = SignTool.verify(endor.signature.toByteArray, NonSignDataOfBlock, certid, sysName)
    } catch {
      case e: RuntimeException =>
        result = false
        resultMsg = s"The endorsement  is not trusted${e.getMessage}"
    }
    (result, resultMsg)
  }

  def VerifyMutliEndorseOfBlock(endorses: Seq[Signature], NonSignDataOfBlock: Array[Byte], sysName: String): (Boolean, String) = {
    var result = true
    var resultMsg = ""
    try {
      breakable(
        endorses.foreach(f => {
          val r = VerifyOneEndorseOfBlock(f, NonSignDataOfBlock, sysName)
          if (!r._1) {
            result = false
            resultMsg = r._2
            break
          }
        }))
    } catch {
      case e: RuntimeException =>
        result = false
        resultMsg = s"The VerifySignOfMultiTrans error,info=${e.getMessage}"
    }
    (result, resultMsg)
  }

  def VerifyAllEndorseOfBlock(block: Block, sysName: String): (Boolean, String) = {
    try {
      val endors = block.endorsements
      val blkOutEndorse = block.clearEndorsements
      VerifyMutliEndorseOfBlock(endors, blkOutEndorse.toByteArray, sysName)
    } catch {
      case e: RuntimeException => (false, s"Endorsement's sign Error,info=${e.getMessage}")
    }
  }
/****************************区块验证签名相关的操作结束**********************************************************/

/****************************验证区块hash相关的操作开始**********************************************************/
  def VerifyHashOfBlock(block: Block): Boolean = {
    var result = false
    try {
      val oldhash = block.hashOfBlock.toStringUtf8()
      val blkOutEndorse = block.clearEndorsements
      val blkOutBlockHash = blkOutEndorse.withHashOfBlock(ByteString.EMPTY)
      val hash = Sha256.hashstr(blkOutBlockHash.toByteArray)
      if (oldhash.equals(hash)) {
        result = true
      }
    } catch {
      case e: RuntimeException =>
        result = false
    }
    result
  }
/****************************验证区块hash相关的操作结束**********************************************************/

/****************************检查背书是否完成开始**********************************************************/
  def EndorsementIsFinishOfBlock(block: Block, NodeNumber: Int): Boolean = {
    var result = false
    try {
      val endorseNumber = block.endorsements.size
      if ((endorseNumber - 1) >= Math.floor(((NodeNumber) * 1.0) / 2)) {
        result = true
      }
    } catch {
      case e: RuntimeException =>
        result = false
    }
    result
  }
/****************************检查背书是否完成结束**********************************************************/

/****************************验证背书信息是否排序的操作开始**********************************************************/
  def sort(src: Array[Signature]):Array[Signature]={
    if(src.length > 1){
      val a = src.slice(1, src.length)
      val b = a.sortWith((l,r)=>IdTool.getSigner4String(l.getCertId)<IdTool.getSigner4String(r.getCertId))
      src.patch(1, b, src.length-1)
    }else{
      src
    }
  }
  
  def verifySort(srclist: Array[Signature]):Int={
    var b: Int = 1
    if(srclist != null && srclist.length > 1){
      val as = sort(srclist)
      breakable(
          for (i <- 0 until srclist.length ) {
            if(IdTool.getSigner4String(srclist(i).getCertId) != IdTool.getSigner4String(as(i).getCertId)){
              b = 0
              break
            }
          }
      )
      b
    }else{
      b = 0
      b
    }
  }
/****************************验证背书信息是否排序的操作结束**********************************************************/
}