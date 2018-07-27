/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Fintech Research Center of ISCAS.
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
 */

package rep.network.consensus.block

import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
import com.trueaccord.scalapb.json.JsonFormat
import rep.app.conf.SystemProfile
import rep.crypto.{ECDSASign, Sha256}
import rep.protos.peer.{Block, Endorsement, Transaction,ChaincodeID}
import rep.utils.TimeUtils
import rep.storage.IdxPrefix
import rep.sc.Shim._
import rep.storage._
import java.security.cert.{ Certificate}
import rep.network.PeerHelper
import rep.utils.SerializeUtils
import scala.util.control.Breaks


/**
  * 出块辅助类
  *
  * @author shidianyue
  * @version 1.0
  * @update 2018-05 jiangbuyun
  **/
object BlockHelper {
  /**
    * 背书块
    * @param blkHash
    * @param alise
    * @return
    */
  def endorseBlock(blkHash:Array[Byte], alise:String):Endorsement ={
    try{
      val (priK, pubK, cert) = ECDSASign.getKeyPair(alise)
      Endorsement(ByteString.copyFromUtf8(ECDSASign.getBitcoinAddrByCert(cert)),
      ByteString.copyFrom(ECDSASign.sign(priK, blkHash)))
    }catch{
      case e:RuntimeException => throw e
    }
  }

  /**
    * 对块的信息进行校验
    * @param blkHash
    * @return
    */
  def checkBlockContent(endor:Endorsement, blkHash: Array[Byte]): Boolean = {
    //获取出块人的背书信息
    try{
        val certTx = ECDSASign.getCertByBitcoinAddr(endor.endorser.toStringUtf8)
        if(certTx.getOrElse(None)!=None){
          //    val certTx = SerializeUtils.deserialise(endor.endorser.toByteArray).asInstanceOf[Certificate]
          val alias = ECDSASign.getAliasByCert(certTx.get).getOrElse(None)
          if (alias == None) false
          else {
            ECDSASign.verify(endor.signature.toByteArray, blkHash, certTx.get.getPublicKey)
          }
        }
        else false
    }catch{
      case e  : RuntimeException => false
    }

  }
  
  //用于对交易对签名验证
  def checkTransaction(t: Transaction, dataAccess: ImpDataAccess): Boolean = {
    var resultMsg = ""
    var result = false
    val sig = t.signature.toByteArray
    val tOutSig1 = t.withSignature(ByteString.EMPTY)
    val tOutSig  = tOutSig1.withMetadata(ByteString.EMPTY)
    
    try{
        val cid = ChaincodeID.fromAscii(t.chaincodeID.toStringUtf8).name
        val certKey = IdxPrefix.WorldStateKeyPreFix + cid + "_" + "CERT_" + t.cert.toStringUtf8 // 普通用户证书的key
        var cert = ECDSASign.getCertWithCheck(t.cert.toStringUtf8,certKey,dataAccess.getSystemName)
        if(cert != None){
          result = ECDSASign.verify(sig, PeerHelper.getTxHash(tOutSig), cert.get.getPublicKey)
        }else{
          resultMsg = s"The transaction(${t.txid}) is not trusted"
        }
      }catch{
        case e : RuntimeException => resultMsg = s"The transaction(${t.txid}) is not trusted${e.getMessage}"
      }
    result
  }

  /**
    * Collect the trans from cache
    * Limit the size with Config param
    *
    * @param trans
    * @return
    */
  def cutTransaction(trans: Seq[Transaction]): Seq[Transaction] = {
    val result = trans.take(if (SystemProfile.getLimitBlockTransNum > trans.length) trans.length else SystemProfile.getLimitBlockTransNum)
    result
  }

  /**
    * 生成原型块
    *
    * @param preBlkHash
    * @param trans
    * @return
    */
  def createPreBlock(preBlkHash: String, trans: Seq[Transaction]): Block = {
    val millis = TimeUtils.getCurrentTime()
    //TODO kami 不应该一刀切，应该针对不同情况采用不同的整合trans的策略
    //TODO kami 出块的时候需要验证交易是否符合要求么？（在内部节点接收的时候已经进行了验证）
    //先这样做确保出块的时候不超出规格
    val blk = new Block(1, Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)),
      trans, _root_.com.google.protobuf.ByteString.EMPTY, _root_.com.google.protobuf.ByteString.EMPTY, Seq())
    blk.withPreviousBlockHash(ByteString.copyFromUtf8(preBlkHash))
  }

  /**
    * Create Genesis Block with config info
    *
    * @return
    */
  def genesisBlockCreator(): Block = {
    //TODO kami priHash Empty
    val blkJson = scala.io.Source.fromFile("json/gensis.json")
    val blkStr = try blkJson.mkString finally blkJson.close()
    val gen_blk = JsonFormat.fromJsonString[Block](blkStr)
    gen_blk
  }

  /**
    * Check the endorsement state
    * Whether its size meet the requirement of candidate
    *
    * @param endorseNum
    * @param candiNum
    * @return
    */
  def checkCandidate(endorseNum: Int, candiNum: Int): Boolean = {
//    if ((endorseNum - 1) > ((candiNum-1) / 2)) true else false
    if ((endorseNum - 1) >= Math.floor(((candiNum)*1.0) / 2)) true else false
  }

  /**
    * 获取块的hash
    * @param blk
    * @return
    */
  def getBlkHash(blk:Block):String = {
    Sha256.hashstr(blk.toByteArray)
  }

  def isEndorserListSorted(srclist : Array[Endorsement]):Int={
		var b : Int = 0
		if (srclist == null || srclist.length < 2){
		  b
    }else{
      if(srclist(0).endorser.toStringUtf8() < srclist(1).endorser.toStringUtf8() ){//升序
        b = 1
      }else{//降序
        b = -1
      }
     
      val loopbreak = new Breaks
        loopbreak.breakable(
          for (i <- 1 to srclist.length-1){
            if(b == 1 && srclist(i).endorser.toStringUtf8() < srclist(i-1).endorser.toStringUtf8()){
               b = 0
               loopbreak.break
            }
            
            if(b == -1 && srclist(i).endorser.toStringUtf8() > srclist(i-1).endorser.toStringUtf8()){
               b = 0
               loopbreak.break
            }
          }
        )
      
    }
		b
	}
  
  
  def main(args: Array[String]): Unit = {
    var eas = new Array[Endorsement](4)
    var e1 = new Endorsement()
    e1 = e1.withEndorser(ByteString.copyFromUtf8("sdfsdfseqqooqoq"))
    e1 = e1.withSignature(ByteString.copyFromUtf8("sdfsdfseqqooqoq"))
    var e2 = new Endorsement()
    e2 = e2.withEndorser(ByteString.copyFromUtf8("hkg"))
    e2 = e2.withSignature(ByteString.copyFromUtf8("hkg"))
    var e3 = new Endorsement()
    e3 = e3.withEndorser(ByteString.copyFromUtf8("wre"))
    e3 = e3.withSignature(ByteString.copyFromUtf8("wre"))
    var e4 = new Endorsement()
    e4 = e4.withEndorser(ByteString.copyFromUtf8("yiu"))
    e4 = e4.withSignature(ByteString.copyFromUtf8("yiu"))
    
    eas(0) = e4
    eas(1) = e1
    eas(2) = e2
    eas(3) = e3
    
    if(isEndorserListSorted(eas) == -1 || isEndorserListSorted(eas) == 0){
      println("not sorted")
    }else{
      println("sorted")
    }
    
    var tmpeas = eas.sortWith((endorser_left,endorser_right)=> endorser_left.endorser.toStringUtf8() < endorser_right.endorser.toStringUtf8())
    
    if(isEndorserListSorted(tmpeas) == -1 || isEndorserListSorted(tmpeas) == 0){
      println("not sorted")
    }else{
      println("sorted")
    }
  }

}
