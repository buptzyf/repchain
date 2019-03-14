/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
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

package rep.network.consensus.block

import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
import com.trueaccord.scalapb.json.JsonFormat
import rep.app.conf.SystemProfile
import rep.crypto.{ Sha256}
import rep.protos.peer.{Block, Signature, Transaction,ChaincodeId,CertId}
import rep.utils.TimeUtils
import rep.storage.IdxPrefix
import rep.sc.Shim._
import rep.storage._
import java.security.cert.{ Certificate}
import rep.network.PeerHelper
import rep.utils.SerializeUtils
import scala.util.control.Breaks
import org.slf4j.LoggerFactory
import rep.crypto.cert.SignTool
import rep.utils.IdTool


/**
  * 出块辅助类
  *
  * @author shidianyue
  * @version 1.0
  * @update 2018-05 jiangbuyun
  **/
object BlockHelper {
  
  protected def log = LoggerFactory.getLogger(this.getClass)
  /**
    * 背书块
    * @param blkHash
    * @param alise
    * @return
    */
  def endorseBlock4NonHash(blkHash:Array[Byte], alise:String):Signature ={
    try{
      
      val millis = TimeUtils.getCurrentTime()
      val certid = IdTool.getCertIdFromName(alise)
      Signature(Option(certid),
          Option(Timestamp(millis/1000 , ((millis % 1000) * 1000000).toInt)),
          ByteString.copyFrom(SignTool.sign4CertId(certid, blkHash)))
    }catch{
      case e:RuntimeException => throw e
    }
  }

  /**
    * 对块的信息进行校验
    * @param blkHash
    * @return
    */
  def checkBlockContent(endor:Signature, blkHash: Array[Byte],sysName:String): Boolean = {
    //获取出块人的背书信息
    try{
      val certid = endor.getCertId
      SignTool.verify(endor.signature.toByteArray, blkHash, certid,sysName)
    }catch{
      case e  : RuntimeException => false
    }

  }
  
  //用于对交易对签名验证
  def checkTransaction(t: Transaction, sysName:String): Boolean = {
    var resultMsg = ""
    var result = false
    //val starttime = System.currentTimeMillis()
    val sig = t.signature
    val tOutSig = t.withSignature(null)
    
    
    try{
      SignTool.verify(sig.get.signature.toByteArray(), tOutSig.toByteArray, sig.get.getCertId,sysName)
       
      }catch{
        case e : RuntimeException => resultMsg = s"The transaction(${t.id}) is not trusted${e.getMessage}"
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
  def createPreBlock(preBlkHash: String, h:Long,trans: Seq[Transaction]): Block = {
    val millis = TimeUtils.getCurrentTime()
    //TODO kami 不应该一刀切，应该针对不同情况采用不同的整合trans的策略
    //TODO kami 出块的时候需要验证交易是否符合要求么？（在内部节点接收的时候已经进行了验证）
    //先这样做确保出块的时候不超出规格
    
    new Block(1, 
        h,
      trans,
     null , 
      _root_.com.google.protobuf.ByteString.EMPTY, 
      ByteString.copyFromUtf8(preBlkHash), 
      Seq())

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

  def isEndorserListSorted(srclist : Array[Signature]):Int={
		var b : Int = 0
		if (srclist == null || srclist.length < 2){
		  b
    }else{
      if(IdTool.getSigner4String(srclist(0).getCertId) < IdTool.getSigner4String(srclist(1).getCertId) ){//升序
        b = 1
      }else{//降序
        b = -1
      }
     
      val loopbreak = new Breaks
        loopbreak.breakable(
          for (i <- 1 to srclist.length-1){
            if(b == 1 && IdTool.getSigner4String(srclist(i).getCertId) < IdTool.getSigner4String(srclist(i-1).getCertId)){
               b = 0
               loopbreak.break
            }
            
            if(b == -1 && IdTool.getSigner4String(srclist(i).getCertId) > IdTool.getSigner4String(srclist(i-1).getCertId)){
               b = 0
               loopbreak.break
            }
          }
        )
      
    }
		b
	}
  
  
  def main(args: Array[String]): Unit = {
    
  }

}
