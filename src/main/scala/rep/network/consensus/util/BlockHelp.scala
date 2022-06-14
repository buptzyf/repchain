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

import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
import scalapb.json4s.JsonFormat
import rep.app.conf.RepChainConfig
import rep.crypto.{BytesHex, Sha256}
import rep.utils.TimeUtils
import rep.crypto.cert.SignTool
import rep.proto.rc2.{Block, BlockHeader, Signature, Transaction}
import rep.utils.IdTool

object BlockHelp {

  private val  versionOfBlock = 1
/****************************背书相关的操作开始**********************************************************/
  def SignDataOfBlock(NonEndorseDataOfBlock: Array[Byte], alise: String,signTool: SignTool): Signature = {
    try {
      val millis = TimeUtils.getCurrentTime()
      val certid = IdTool.getCertIdFromName(alise)
      Signature(
        Option(IdTool.getCertIdFromName(IdTool.getCompleteSignerName(signTool.getConfig,alise)) ),
        Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)),
        ByteString.copyFrom(signTool.sign4CertId(certid, NonEndorseDataOfBlock)))
    } catch {
      case e: RuntimeException => throw e
    }
  }

  def SignBlock(block: BlockHeader, alise: String,signTool: SignTool): Signature = {
    try {
      val tmpblock = block.clearEndorsements
      SignDataOfBlock(tmpblock.toByteArray, alise,signTool)
    } catch {
      case e: RuntimeException => throw e
    }
  }

  def AddHeaderSignToBlock(block: BlockHeader, alise: String,signTool: SignTool): BlockHeader = {
    try {
      var signdata = SignBlock(block, alise,signTool)
      AddEndorsementToBlock(block, signdata)
    } catch {
      case e: RuntimeException => throw e
    }
  }

  def AddEndorsementToBlock(block: BlockHeader, signdata: Signature): BlockHeader = {
    try {
      if (block.endorsements.isEmpty) {
        block.withEndorsements(Seq(signdata))
      } else {
        block.withEndorsements(block.endorsements.+:(signdata))
      }
    } catch {
      case e: RuntimeException => throw e
    }
  }
  
  
/****************************背书相关的操作结束**********************************************************/

  //该方法在预执行结束之后才能调用
  def AddBlockHeaderHash(block: Block,sha256: Sha256): Block = {
    try {
      val hash = GetBlockHeaderHash(block.getHeader,sha256)
      val header = block.getHeader.withHashPresent(ByteString.copyFromUtf8(hash))
      block.withHeader(header)
    } catch {
      case e: RuntimeException => throw e
    }
  }

  def GetBlockHeaderHash(header: BlockHeader,sha256: Sha256): String = {
    try {
      val headerOutEndorse = header.clearEndorsements
      val headerOutBlockHash = headerOutEndorse.withHashPresent(ByteString.EMPTY)
      sha256.hashstr(headerOutBlockHash.toByteArray)
    } catch {
      case e: RuntimeException => throw e
    }
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-14
   * @category	打包交易，建立新的区块
   * @param	preBlockHash:String 上一个区块的Hash,h:Long 上一个区块的高度，trans: Seq[Transaction]交易列表
   * @return 返回Block
   * */
  def buildBlock(preBlockHash: String, h: Long, trans: Seq[Transaction]): Block = {
    try {
      val blockHeader = new BlockHeader(2,h,ByteString.EMPTY,ByteString.EMPTY,ByteString.EMPTY,
        ByteString.copyFromUtf8(preBlockHash),ByteString.EMPTY,ByteString.EMPTY,0l,Seq.empty)
      new Block(Some(blockHeader),trans,Seq.empty,None)
    } catch {
      case e: RuntimeException => throw e
    }
  }


  def CreateGenesisBlock(chainConfig: RepChainConfig):Block={
    var gen_blk : Block = null
    var genesisFileName = "genesis.json"
    if(chainConfig.isUseGM){
      genesisFileName = "genesis_gm.json"
    }

    try{
      val blkJson = scala.io.Source.fromFile(s"json/${chainConfig.getChainNetworkId}/"+genesisFileName,"UTF-8")
      val blkStr = try blkJson.mkString finally blkJson.close()
      gen_blk = JsonFormat.fromJsonString[Block](blkStr)
    }catch {
      case e:Exception=>
        e.printStackTrace()
    }
    gen_blk

  }

  def preTransaction(tr: String): Option[Transaction] = {
      try {
        val tr1 = BytesHex.hex2bytes(tr) // 解析交易编码后的16进制字符串,进行解码16进制反解码decode
        var txr = Transaction.defaultInstance
        txr = Transaction.parseFrom(tr1)
        Some(txr)
      } catch {
        case e: Exception =>
          None
      }
  }


}