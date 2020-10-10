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

package rep.storage

import rep.storage.block._
import rep.storage.leveldb._
import rep.storage.cfg._
import rep.protos.peer._

import scala.collection.JavaConverters._
import com.google.protobuf.ByteString
import com.fasterxml.jackson.core.Base64Variants
import rep.crypto._
import org.json4s._
import org.json4s.jackson.JsonMethods
import rep.sc.Shim._
import rep.utils._
import java.io._

import rep.api.rest.RestActor.BlockTime
import rep.protos.peer.OperLog

import scala.collection.mutable._
import rep.network.consensus.util.BlockHelp
import rep.log.RepLogger
import rep.utils.SerializeUtils.deserialise
import rep.storage.util.pathUtil
import rep.log.RepTimeTracer
import rep.app.conf.SystemProfile
import rep.crypto.cert.certCache

import scala.util.control.Breaks._

/**
 * @author jiangbuyun
 * @version	0.7
 * @since	2017-09-28
 * @category	实现IDataAccess定义的外部操作方法。
 */
class ImpDataAccess private (SystemName: String) extends IDataAccess(SystemName) {
  private var filemgr: BlockFileMgr = null

  //初始化文件操作实例
  //var bi: BlockInstances = BlockInstances.getDBInstance()

  filemgr = new BlockFileMgr(this.SystemName)

  //private var chainInfoCache: ChainInfoInCache = new ChainInfoInCache(this)

  private var bheight4bidx = new HashMap[Long, blockindex]()
  private var txid4bheight = new HashMap[String, Long]()

  def loadBlockInfoToCache: Boolean = {
    var b: Boolean = false
    val chaininfo = this.getBlockChainInfo()
    val mh = chaininfo.height
    var h: Long = 2
    var start : Long = System.currentTimeMillis()
    while (h <= mh) {
      val hidx = this.getBlockIdxByHeight(h)
      bheight4bidx += h -> hidx
      val ts = hidx.getTxIds()
      ts.foreach(f => {
        txid4bheight += f -> h
      })
      h += 1 
      if(h % 1000 == 0){
        var end1 = System.currentTimeMillis()
        println(h+" load time ="+(end1 - start)/1000+"s")
      }
    }
    var end = System.currentTimeMillis()
    println(s"load finish,h=${h},mh=${mh} time ="+(end - start)/1000+"s")
    b
  }

  def isFinish: String = {
    var b = "0"
    val chaininfo = this.getBlockChainInfo()
    var idx = this.bheight4bidx(chaininfo.height)
    if (idx != null) {
      b = "1"
    }
    b
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	根据block的base64 hash值获取block
   * @param	base64 String block的hash64值
   * @return	返回block的字节数组，如果没有找到，返回null
   */
  override def getBlockByBase64Hash(base64: String): Array[Byte] = {
    var rb: Array[Byte] = null
    var bh: String = null
    if (base64 != null) {
      try {
        val bstr = Base64Variants.getDefaultVariant.decode(base64)
        bh = new String(bstr)
        rb = getBlockByHash(bh)
      } catch {
        case e: Exception => {
          RepLogger.error(
            RepLogger.Storager_Logger,
            "base64 is invalidate")
        }
      }
    }

    rb
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	根据block的hash值获取block
   * @param	bh String block的hash值
   * @return	返回block的字节数组，如果没有找到，返回null
   */
  override def getBlockByHash(bh: String): Array[Byte] = {
    var rb: Array[Byte] = null
    val key = IdxPrefix.IdxBlockPrefix + bh
    val value = this.Get(key)
    //val keylist = this.FindKeyByLike("b_", 1)
    if (value != null) {
      var bidx = new blockindex()
      bidx.InitBlockIndex(value)
      rb = filemgr.readBlock(bidx.getBlockFileNo(), bidx.getBlockFilePos(), bidx.getBlockLength())
      //rb = bhelp.readBlock(bidx.getBlockFileNo(), bidx.getBlockFilePos(), bidx.getBlockLength())
    }
    rb
  }

  override def getBlockHeightByHash(bh: String): Long = {
    var rh: Long = -1l
    val key = IdxPrefix.IdxBlockPrefix + bh
    val value = this.Get(key)
    //val keylist = this.FindKeyByLike("b_", 1)
    if (value != null) {
      var bidx = new blockindex()
      bidx.InitBlockIndex(value)
      rh = bidx.getBlockHeight()
    }
    rh
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	根据block的hash值获取block
   * @param	bh String block的hash值
   * @return	返回block的字节数组，如果没有找到，返回null
   */
  private def getBlockIdxByHash(bh: String): blockindex = {
    var rb: blockindex = null
    val key = IdxPrefix.IdxBlockPrefix + bh
    val value = this.Get(key)
    if (value != null) {
      rb = new blockindex()
      rb.InitBlockIndex(value)
    }
    rb
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	根据高度获取Block的index
   * @param	h Long block的高度值
   * @return	返回blockindex，如果没有找到，返回null
   */
  //private
  def getBlockIdxByHeight(h: Long): blockindex = {
    var rb: blockindex = null
    val key = IdxPrefix.IdxBlockHeight + String.valueOf(h)
    val value = this.Get(key)
    if (value != null) {
      val bkey = this.byteToString(value)
      if (!bkey.equalsIgnoreCase("")) {
        rb = getBlockIdxByHash(bkey)
      }
    }
    rb
  }

  def getBlockHashByHeight(h: Long): String = {
    var rs = ""
    val bidx = getBlockIdxByHeight(h)
    if (bidx != null) {
      rs = bidx.getBlockHash()
    }
    rs
  }

  def getNumberOfTransInBlockByHeight(h: Long): Int = {
    /*var rs = 0
    val bidx = getBlockIdxByHeight(h)
    if (bidx != null) {
      rs = bidx.getNumberOfTrans
    }
    rs*/
    var rs = 0
    val bidx = this.bheight4bidx(h)
    if (bidx != null) {
      rs = bidx.getNumberOfTrans
    }
    rs
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	根据交易id获取这个交易隶属的block
   * @param	bh String 交易的id
   * @return	返回Block字节数组，如果没有找到，返回null
   */
  override def getBlockByTxId(bh: String): Array[Byte] = {
    var rb: Array[Byte] = null
    val key = IdxPrefix.IdxTransaction + bh
    val value = this.Get(key)
    val bkey = this.byteToString(value)
    if (!bkey.equalsIgnoreCase("")) {
      rb = getBlockByHash(bkey)
    }
    rb
  }

  def getBlockIdxByTxid(bh: String): blockindex = {
    var bidx: blockindex = null
    val key = IdxPrefix.IdxTransaction + bh
    val value = this.Get(key)
    val bkey = this.byteToString(value)
    if (!bkey.equalsIgnoreCase("")) {
      bidx = this.getBlockIdxByHash(bkey)
    }
    bidx
  }

  /**
   * @author jiangbuyun
   * @version	1.0
   * @since	2019-06-12
   * @category	根据交易id检查交易是否存在
   * @param	txid String 交易的id
   * @return	返回true表示存在；如果没有找到，返回false
   */
  def isExistTrans4Txid(txid: String): Boolean = {
    var b = false
    val key = IdxPrefix.IdxTransaction + txid
    val value = this.Get(key)
    if(value != null){
      b = true
    }
    /*val bkey = this.byteToString(value)
    if (!bkey.equalsIgnoreCase("")) {
      b = true
    }*/
    b
  }

  /**
   * @author jiangbuyun
   * @version	1.0
   * @since	2019-04-28
   * @category	根据交易id获取这个交易隶属的block
   * @param	bh String 交易的id
   * @return	返回Block对象，如果没有找到，返回null
   */
  def getBlock4ObjectByTxId(bh: String): Block = {
    val bb = getBlockByTxId(bh)
    bb match {
      case null => null
      case _ =>
        Block.parseFrom(bb)
    }
  }

  /**
   * @author jiangbuyun
   * @version	1.0
   * @since	2019-04-28
   * @category	根据交易Id值获取指定交易
   * @param	txid String 交易的Id值
   * @return	返回Transaction对象，如果没有找到，返回None
   */
  def getTransDataByTxId(txid: String): Option[Transaction] = {
    val b = getBlock4ObjectByTxId(txid)
    b match {
      case null => None
      case _ =>
        b.transactions.find(_.id == txid)
    }
  }

  /**
   * @author jiangbuyun
   * @version	1.0
   * @since	2019-04-28
   * @category	根据chaincodeid获取指定交易
   * @param	cid String 链码id
   * @return	返回Transaction对象，如果没有找到，返回None
   */
  def getTransOfContractFromChaincodeId(cid: String): Option[Transaction] = {
    val txid = Get(cid)
    if (txid == null) {
      None
    } else {
      getTransDataByTxId(deserialise(txid).asInstanceOf[String])
    }
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	根据链的高度来获取等于这个高度值的block
   * @param	h Long block的高度值
   * @return	返回Block字节数组，如果没有找到，返回null
   */
  override def getBlockByHeight(h: Long): Array[Byte] = {
    var rb: Array[Byte] = null
    val key = IdxPrefix.IdxBlockHeight + String.valueOf(h)
    val value = this.Get(key)
    if (value != null) {
      val bkey = this.byteToString(value)
      if (!bkey.equalsIgnoreCase("")) {
        rb = getBlockByHash(bkey)
      }
    }
    rb
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	从某个高度开始（包含这个高度）的所有块取出来，h：开始的高度，h 必须大于0，高度是从1开始
   * limits 返回块的数量，默认值为0，如果大于0，返回指定数量的块，如果块数小于该值返回实际数量
   * 默认最多1000条
   * @param	h Int block的高度值，limits 返回记录的条数
   * @return	返回指定长度的Block数组，如果没有找到，返回长度=0的数组
   */
  override def getBlocksFromHeight(h: Int, limits: Int): Array[Array[Byte]] = {
    var tlimits = limits
    var rb = new scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    var currentheightvalue: Int = this.getBlockHeight().intValue()
    var l: Int = h
    if (h > 0 && h <= currentheightvalue) {
      var goon: Boolean = true
      var count = 0
      if (tlimits == 0) {
        tlimits = 1000
      }
      for (l <- h to currentheightvalue if goon) {
        if (count < tlimits) {
          val bb = this.getBlockByHeight(l)
          rb += bb
          count += 1
        } else {
          goon = false
        }

      }
    }
    rb.toArray
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	从某个高度开始（包含这个高度）的所有块取出来，h：开始的高度，h 必须大于0，高度是从1开始
   * limits 返回块的数量，默认值为0，如果大于0，返回指定数量的块，如果块数小于该值返回实际数量
   * 默认最多1000条
   * @param	h Int block的高度值
   * @return	返回最多1000条的Block数组，如果没有找到，返回长度=0的数组
   */
  override def getBlocksFromHeight(h: Int): Array[Array[Byte]] = {
    var rb: Array[Array[Byte]] = getBlocksFromHeight(h, 0)
    rb
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	从某个高度开始（包含这个高度）的所有块取出来，h：开始的高度，h 必须大于0，高度是从1开始
   * limits 返回块的数量，默认值为0，如果大于0，返回指定数量的块，如果块数小于该值返回实际数量
   * 默认最多1000条
   * @param	h Int block的高度值，limits 返回记录的条数
   * @return	返回指定长度的Block数组，如果没有找到，返回长度=0的数组
   */
  override def getBlocks4ObjectFromHeight(h: Int, limits: Int): Array[Block] = {
    var rbo: Array[Block] = null
    var rb: Array[Array[Byte]] = getBlocksFromHeight(h, limits)
    if (rb != null && rb.length > 0) {
      rbo = new Array[Block](rb.length)
      var i = 0
      for (i <- 0 until (rb.length)) {
        val tmpb = Block.parseFrom(rb(i))
        rbo(i) = tmpb
      }
    }
    rbo
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	从某个高度开始（包含这个高度）的所有块取出来，h：开始的高度，h 必须大于0，高度是从1开始
   * limits 返回块的数量，默认值为0，如果大于0，返回指定数量的块，如果块数小于该值返回实际数量
   * 默认最多1000条
   * @param	h Int block的高度值
   * @return	返回最多1000条的Block数组，如果没有找到，返回长度=0的数组
   */
  override def getBlocks4ObjectFromHeight(h: Int): Array[Block] = {
    var rbo: Array[Block] = null
    rbo = getBlocks4ObjectFromHeight(h, 0)
    rbo
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	根据链的高度来获取等于这个高度值的block
   * @param	h Long block的高度值
   * @return	返回Block字节数组，如果没有找到，返回null
   */
  override def getBlock4ObjectByHeight(h: Long): Block = {
    var rb: Array[Byte] = this.getBlockByHeight(h)
    var rbo: Block = null
    if (rb != null) {
      rbo = Block.parseFrom(rb)
    }
    rbo
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2019-09-11
   * @category	根据交易ID获取交易入块时间
   * @param	txid 交易id
   * @return	返回出块时间
   */
  override def getBlockTimeOfTxid(txid: String): String = {
    //SerializeUtils.toJson(this.getBlockTime4Block(this.getBlock4ObjectByTxId(txid)))
    SerializeUtils.toJson(this.getBlockTime4Block(txid))
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2019-09-11
   * @category	根据块的高度获取交易入块时间
   * @param	h 块高度
   * @return	返回出块时间
   */
  def getBlockTimeOfHeight(h: Long): String = {
    SerializeUtils.toJson(this.getBlockTime4BlockByHeight(h))
  }

  /*private def getBlockTime4Block(b: Block): BlockTime = {
    var rs = BlockTime("", "")
    if (b != null && b.endorsements != null && b.endorsements.length >= 1) {
      val signer = b.endorsements(0)
      val date = new java.util.Date(signer.tmLocal.get.seconds * 1000);
      val formatstr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      formatstr.setTimeZone(java.util.TimeZone.getTimeZone("ETC/GMT-8"))
      val tmpstr = formatstr.format(date)
      val millis = signer.tmLocal.get.nanos / 1000000
      val createTime = tmpstr + "." + millis
      // 13位,毫秒精度级(utc时间)
      val createTimeUtc = String.valueOf((signer.tmLocal.get.seconds * 1000 + millis) - 8 * 3600 * 1000)
      rs = BlockTime(createTime, createTimeUtc)
    }
    rs
  }*/

  private def getBlockTime4Block(txid: String): BlockTime = {
    /*var rs = BlockTime("", "")
    val idx = this.getBlockIdxByTxid(txid)
    if (idx != null) {
      rs = BlockTime(idx.getCreateTime, idx.getCreateTimeUtc)
    }
    rs*/
    var rs = BlockTime("", "")
    try {
      val th = this.txid4bheight(txid)
      val idx = this.bheight4bidx(th)
      if (idx != null) {
        rs = BlockTime(idx.getCreateTime, idx.getCreateTimeUtc)
      }
    } catch {
      case e: Exception =>
    }
    rs
  }

  private def getBlockTime4BlockByHeight(height: Long): BlockTime = {
    /*var rs = BlockTime("", "")
    val idx = this.getBlockIdxByHeight(height)
    if (idx != null) {
      rs = BlockTime(idx.getCreateTime, idx.getCreateTimeUtc)
    }
    rs*/
    var rs = BlockTime("", "")
    val idx = this.bheight4bidx(height)
    if (idx != null) {
      rs = BlockTime(idx.getCreateTime, idx.getCreateTimeUtc)
    }

    rs
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	返回当前区块链的chaininfo
   * @param	无
   * @return	返回链码信息 BlockchainInfo
   */
  override def getBlockChainInfo(): BlockchainInfo = {
    var rbc = new BlockchainInfo()
    val currentheight = this.getBlockHeight()
    val currenttxnumber = this.getBlockAllTxNumber()
    val bidx = this.getBlockIdxByHeight(currentheight)
    if (bidx != null) {
      val bhash = bidx.getBlockHash()
      val bprevhash = bidx.getBlockPrevHash()
      val statehash = bidx.getStateHash()
      if (bhash != null && !bhash.equalsIgnoreCase("")) {
        rbc = rbc.withCurrentBlockHash(ByteString.copyFromUtf8(bhash))
      } else {
        rbc = rbc.withCurrentBlockHash(_root_.com.google.protobuf.ByteString.EMPTY)
      }

      if (bprevhash != null && !bprevhash.equalsIgnoreCase("")) {
        rbc = rbc.withPreviousBlockHash(ByteString.copyFromUtf8(bprevhash))
      } else {
        rbc = rbc.withPreviousBlockHash(_root_.com.google.protobuf.ByteString.EMPTY)
      }

      if (statehash != null && !statehash.equalsIgnoreCase("")) {
        rbc = rbc.withCurrentStateHash(ByteString.copyFromUtf8(statehash))
      } else {
        rbc = rbc.withCurrentStateHash(_root_.com.google.protobuf.ByteString.EMPTY)
      }

    }

    rbc = rbc.withHeight(currentheight)
    rbc = rbc.withTotalTransactions(currenttxnumber)

    rbc
    //chainInfoCache.getBlockChainInfo()
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	判断给定的新块是否是最后一个区块
   * @param	newblock Block 待判断的块,lastblock Block 已知的最后区块
   * @return	如果是最后的区块，返回true；否则，返回false
   */
  /*private def isLastBlock(newblock: Block, lastblock: blockindex): Boolean = {
    var b: Boolean = false
    if (lastblock == null) {
      if (newblock.previousBlockHash.isEmpty()) {
        b = true
      } else {
        b = false
      }
    } else {
      val prve = newblock.previousBlockHash.toStringUtf8()
      //目前直接从上一个块中获取
      //val cur = lastblock.hashOfBlock.toStringUtf8()
      //if (prve.equals(cur) && (lastblock.height + 1) == newblock.height) {
      if (prve == lastblock.getBlockHash()) {
        b = true
      } else {
        b = false
      }
    }
    b
  }*/

  private def isLastBlock(newblock: Block, lastblockhash: String): Boolean = {
    var b: Boolean = false
    if (lastblockhash == null) {
      if (newblock.previousBlockHash.isEmpty()) {
        b = true
      } else {
        b = false
      }
    } else {
      val prve = newblock.previousBlockHash.toStringUtf8()
      //目前直接从上一个块中获取
      //val cur = lastblock.hashOfBlock.toStringUtf8()
      //if (prve.equals(cur) && (lastblock.height + 1) == newblock.height) {
      if (prve == lastblockhash) {
        b = true
      } else {
        b = false
      }
    }
    b
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	同时写入多个区块到系统
   * @param	blocks Array[Block] 待写入系统的区块数组
   * @return	返回成功写入的区块的数量
   */
  override def restoreBlocks(blocks: Array[Block]): Int = {
    var count = 0
    if (blocks == null) return count
    if (blocks.length <= 0) return count
    blocks.foreach(blk => {
      try {
        val b = this.restoreBlock(blk)
        if (b._1) {
          count += 1
        } else {
          throw new Exception("restore block unexpected error")
        }
      } catch {
        case e: Exception => {
          throw e
        }
      }
    })
    count
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	根据交易id获取chaincode id
   * @param	block Block 待写入的区块,txid String 待查询的交易id
   * @return	如果成功返回chaincode id，否则返回空字符串
   */
  private def getTxidFormBlock(block: Block, txid: String): String = {
    var rel = ""
    if (block != null) {
      var trans = block.transactions
      if (trans.length > 0) {
        breakable(
          trans.foreach(f => {
            if (f.id.equals(txid)) {
              //rel = f.getPayload.getChaincodeID.name
              //根据合约编写的时候不添加版本好的规则生成
              //rel = IdTool.getCid(f.cid.get)
              rel = f.getCid.chaincodeName
              break
            }
          }))
      }
    }
    rel
  }

  private def getCidFormBlock(block: Block, rindex: Int,txid:String): String = {
    var rel = ""
    if (block != null) {
      var trans = block.transactions
      if(rindex >0 && rindex < trans.length){
        val t = trans(rindex)
        if (t.id.equals(txid)) {
          rel = t.getCid.chaincodeName
        }
      }
    }
    rel
  }

  private def isChangeCertStatus(block: Block, txid: String): Boolean = {
    var rel = false
    if (block != null) {
      var trans = block.transactions
      if (trans.length > 0) {
        breakable(
          trans.foreach(f => {
            if (f.id.equals(txid) && f.getCid.chaincodeName == SystemProfile.getAccountChaincodeName && f.`type` == rep.protos.peer.Transaction.Type.CHAINCODE_INVOKE && f.para.ipt.get.function == SystemProfile.getCertStatusChangeFunction) {
              rel = true
              break
            }
          }))
      }
    }
    rel
  }

  private def isChangeCertStatus(block: Block, rindex:Int,txid: String): Boolean = {
    var rel = false
    if (block != null) {
      var trans = block.transactions
      if(rindex >0 && rindex < trans.length){
        val f = trans(rindex)
        if (f.id.equals(txid) && f.getCid.chaincodeName == SystemProfile.getAccountChaincodeName && f.`type` == rep.protos.peer.Transaction.Type.CHAINCODE_INVOKE && f.para.ipt.get.function == SystemProfile.getCertStatusChangeFunction) {
          rel = true
        }
      }
      /*if (trans.length > 0) {
        breakable(
          trans.foreach(f => {
            if (f.id.equals(txid) && f.getCid.chaincodeName == SystemProfile.getAccountChaincodeName && f.`type` == rep.protos.peer.Transaction.Type.CHAINCODE_INVOKE && f.para.ipt.get.function == SystemProfile.getCertStatusChangeFunction) {
              rel = true
              break
            }
          }))
      }*/
    }
    rel
  }

  private def WriteOperLogToDBWithRestoreBlock(block: Block) = {
    try {
      //var writetodbtxidserial = "write txid result to db for serial="
      val txresults = block.transactionResults
      if (!txresults.isEmpty) {
        val len = txresults.length -1
        for( i <- 0 to len){
          val f = txresults(i)
          val txid = f.txId
          val cid = getCidFormBlock(block, i,txid)
          val changeCertStatus = isChangeCertStatus(block,i, txid)
          val logs = f.ol

          if (logs != null && logs.length > 0) {
            logs.foreach(f => {
              var fkey = f.key
              if (fkey.startsWith(IdxPrefix.WorldStateKeyPreFix)) {
                this.Put(f.key, f.newValue.toByteArray())
              } else {
                fkey = IdxPrefix.WorldStateKeyPreFix + cid + "_" + f.key
                this.Put(fkey, f.newValue.toByteArray())
              }
              //需要通知证书缓存修改证书状态
              if (changeCertStatus) {
                certCache.CertStatusUpdate(fkey)
              }
            })
          }
        }
      }



      /*val txresults = block.transactionResults
      if (!txresults.isEmpty) {
        txresults.foreach(f => {
          val txid = f.txId
          //writetodbtxidserial = writetodbtxidserial + txid + ","
          val cid = getTxidFormBlock(block, txid)
          val changeCertStatus = isChangeCertStatus(block, txid)
          val logs = f.ol

          if (logs != null && logs.length > 0) {
            logs.foreach(f => {
              var fkey = f.key
              if (fkey.startsWith(IdxPrefix.WorldStateKeyPreFix)) {
                this.Put(f.key, f.newValue.toByteArray())
              } else {
                fkey = IdxPrefix.WorldStateKeyPreFix + cid + "_" + f.key
                this.Put(fkey, f.newValue.toByteArray())
              }
              //需要通知证书缓存修改证书状态
              if (changeCertStatus) {
                certCache.CertStatusUpdate(fkey)
              }
            })
          }
        })
        //RepLogger.error(RepLogger.Business_Logger,  s" current block height=${block.height},trans write serial: ${writetodbtxidserial}")
      }*/
    } catch {
      case e: RuntimeException => throw e
    }
  }

  override def rollbackToheight(toHeight: Long): Boolean = {
    val rs = new Rollback4Storager(this, filemgr)
    val b = rs.rollbackToheight(toHeight)
    //chainInfoCache.initChainInfo
    b
  }



  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	写入单个区块到系统
   * @param	block  待写入系统的区块
   * @return	如果成功返回true，否则返回false
   */
  override def restoreBlock(block: Block): (Boolean, Long, Long, String, String, String) = {
    if (block == null) return (false, 0l, 0l, "", "", "")
    if (block.hashOfBlock == null || block.hashOfBlock.isEmpty()) return (false, 0l, 0l, "", "", "")
    synchronized {
      val oldh = getBlockHeight()
      val oldno = this.getMaxFileNo()
      val oldtxnumber = this.getBlockAllTxNumber()
      var prevblockhash: String = null

      if (oldh > 0) {
        RepTimeTracer.setStartTime(this.SystemName, "storage-save-get-preblock", System.currentTimeMillis(), block.height, block.transactions.size)
        //val tbs = this.getBlockByHeight(oldh)
        //prevblock = getBlockIdxByHeight(block.height - 1)
        prevblockhash = this.getLastBlockHash(block.height - 1)
        //prevblock = Block.parseFrom(tbs)
        RepTimeTracer.setEndTime(this.SystemName, "storage-save-get-preblock", System.currentTimeMillis(), block.height, block.transactions.size)
      }

      if (isLastBlock(block, prevblockhash)) {
        try {
          this.BeginTrans
          RepTimeTracer.setStartTime(this.SystemName, "storage-save-write-operlog", System.currentTimeMillis(), block.height, block.transactions.size)
          WriteOperLogToDBWithRestoreBlock(block)
          RepTimeTracer.setEndTime(this.SystemName, "storage-save-write-operlog", System.currentTimeMillis(), block.height, block.transactions.size)
          RepTimeTracer.setStartTime(this.SystemName, "storage-save-commit", System.currentTimeMillis(), block.height, block.transactions.size)
          if (this.commitAndAddBlock(block, oldh, oldno, oldtxnumber)) {
            RepTimeTracer.setEndTime(this.SystemName, "storage-save-commit", System.currentTimeMillis(), block.height, block.transactions.size)
            this.CommitTrans
            /*chainInfoCache.setHeight(block.height)
            chainInfoCache.setTXNumber(oldtxnumber + block.transactions.length)
            chainInfoCache.setBlockHash(block.hashOfBlock.toStringUtf8())
            chainInfoCache.setPrevBlockHash(block.previousBlockHash.toStringUtf8())
            chainInfoCache.setBlockStateHash(block.stateHash.toStringUtf8())*/
            (true, block.height, oldtxnumber + block.transactions.length, block.hashOfBlock.toStringUtf8(), block.previousBlockHash.toStringUtf8(), block.stateHash.toStringUtf8())
          } else {
            this.RollbackTrans
            (false, 0l, 0l, "", "", "")
          }
        } catch {
          case e: Exception => {
            this.RollbackTrans
            throw e
          }
        } finally {
          //todo
        }

      } else {
        throw new Exception("This block is not last block")
      }

    }
    //b
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	内部函数，完成区块写入的所有的工作，包含索引的生成，区块字节写入到文件，以及Merkle的生成
   * @param	_block Array[Block] 待写入系统的区块
   * @return	成功返回true，否则false
   */
  private def commitAndAddBlock(block: Block, oldh: Long, oldno: Int, oldtxnumber: Long): Boolean = {
    var b: Boolean = false
    //var block = _block
    if (block == null) return b
    if (block.hashOfBlock == null || block.hashOfBlock.isEmpty()) return b
    if (block.previousBlockHash == null) return b
    RepLogger.trace(
      RepLogger.Storager_Logger,
      "system_name=" + this.SystemName + "\t store a block")
    synchronized {
      try {
        var bidx = new blockindex()
        bidx.InitBlockIndex(block)
        var newh = block.height
        setBlockHeight(newh)
        this.setLastBlockHash(block.hashOfBlock.toStringUtf8)

        var newno = oldno
        var newtxnumber = oldtxnumber
        val rbb = block.toByteArray
        val blenght = rbb.length
        //jiangbuyun modify 20180430,块写入文件系统时，增加块长度写入文件中，方便以后没有leveldb时，可以完全依靠块文件快速恢复系统，判断长度是否超过文件的最大长度
        if (filemgr.isAddFile(oldno, blenght + 8)) {
          newno = oldno + 1
          setMaxFileNo(newno)
          setFileFirstHeight(newno, newh)
        } else if (newh == 1 && newno == 0) {
          setFileFirstHeight(newno, newh)
        }
        val startpos = filemgr.getFileLength(newno)
        bidx.setBlockFileNo(newno)
        //jiangbuyun modify 20180430,块写入文件系统时，增加块长度写入文件中，方便以后没有leveldb时，可以完全依靠块文件快速恢复系统，调整块数据的写入初始位置
        //bidx.setBlockFilePos(startpos)
        bidx.setBlockFilePos(startpos + 8)
        bidx.setBlockLength(blenght)


        RepLogger.trace(
          RepLogger.Storager_Logger,
          "system_name=" + this.SystemName + "\t new height=" + newh + "\t new file no=" + newno + "\t new tx number=" + newtxnumber)

        this.Put(IdxPrefix.IdxBlockPrefix + bidx.getBlockHash(), bidx.toArrayByte())
        RepLogger.trace(
          RepLogger.Storager_Logger,
          "system_name=" + this.SystemName + "\t blockhash=" + bidx.getBlockHash())

        this.Put(IdxPrefix.IdxBlockHeight + newh, bidx.getBlockHash().getBytes())
        val ts = bidx.getTxIds()
        if (ts != null && ts.length > 0) {
          ts.foreach(f => {
            this.Put(IdxPrefix.IdxTransaction + f, bidx.getBlockHash().getBytes())
            newtxnumber += 1
          })
        }
        this.setBlockAllTxNumber(newtxnumber)
        //jiangbuyun modify 20180430,块写入文件系统时，增加块长度写入文件中，方便以后没有leveldb时，可以完全依靠块文件快速恢复系统,该位置实现字节数组的合并
        //bhelp.writeBlock(bidx.getBlockFileNo(), bidx.getBlockFilePos(), rbb)
        RepTimeTracer.setStartTime(this.SystemName, "storage-save-write-file", System.currentTimeMillis(), block.height, block.transactions.size)
        filemgr.writeBlock(bidx.getBlockFileNo(), bidx.getBlockFilePos() - 8, pathUtil.longToByte(blenght) ++ rbb)
        RepTimeTracer.setEndTime(this.SystemName, "storage-save-write-file", System.currentTimeMillis(), block.height, block.transactions.size)
        b = true

        RepLogger.trace(
          RepLogger.Storager_Logger,
          "system_name=" + this.SystemName + "\t blockhash=" + bidx.getBlockHash() + "\tcommited success")
      } catch {
        case e: Exception => {
          this.setBlockHeight(oldh)
          this.setMaxFileNo(oldno)
          this.setBlockAllTxNumber(oldtxnumber)
          e.printStackTrace()
          throw e
        }
      } finally {
        //todo
      }
    }

    b
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	内部函数，待写入区块的hash值验证
   * @param	block Block 待写入的区块,blockhsah Array[Byte] 区块的hash值
   * @return	成功返回true，否则false
   */
  /*private def commitAndAddBlock(block: Block, blockhsah: Array[Byte]): Boolean = {
    var b: Boolean = false
    if (block == null) return b
    if (block.hashOfBlock == null || block.hashOfBlock.isEmpty()) return b
    if (block.previousBlockHash == null) return b
    if (blockhsah == null) return b
    val rbb = block.toByteArray
    val bh = Sha256.hashstr(rbb)
    val blockhashStr = new String(blockhsah)
    if (bh.equalsIgnoreCase(blockhashStr)) {
      b = commitAndAddBlock(block)
    }
    b
  }*/

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	获取链码的高度
   * @param	无
   * @return	成功返回当前区块链的高度 Long
   */
  override def getBlockHeight(): Long = {
    var l: Long = this.toLong(this.Get(IdxPrefix.Height))
    if (l == -1) l = 0
    return l
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	内部函数，设置区块高度
   * @param	h Long 高度
   * @return	无
   */
  private def setBlockHeight(h: Long) = {
    this.Put(IdxPrefix.Height, String.valueOf(h).getBytes())
  }

  private def setLastBlockHash(lasthash:String):Boolean = {
    this.Put(IdxPrefix.LastHash,lasthash.getBytes)
  }

  private def getLastBlockHash(h:Long):String = {
    var r : String= null
    val hash = this.Get(IdxPrefix.LastHash)
    if(hash == null){
      val prevblock = getBlockIdxByHeight(h)
      if(prevblock != null){
        r = prevblock.getBlockHash()
      }
    }else{
      r = new String(hash)
    }
    r
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	获取系统交易的数量
   * @param	无
   * @return	返回系统当前的交易数量
   */
  override def getBlockAllTxNumber(): Long = {
    var l: Long = this.toLong(this.Get(IdxPrefix.TotalAllTxNumber))
    if (l == -1) l = 0
    return l
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	内部函数，设置系统当前的交易总量，建立交易索引用
   * @param	num Long 交易数量
   * @return	无
   */
  private def setBlockAllTxNumber(num: Long) = {
    this.Put(IdxPrefix.TotalAllTxNumber, String.valueOf(num).getBytes())
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	获取当前存储区块字节的文件编号
   * @param	无
   * @return	返回文件编号
   */
  override def getMaxFileNo(): Int = {
    var l: Int = this.toInt(this.Get(IdxPrefix.MaxFileNo))
    if (l == -1) l = 0
    return l
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	内部函数，设置系统最大的存储文件的编号
   * @param	no Int 最大的区块文件编号
   * @return	无
   */
  private def setMaxFileNo(no: Int) = {
    this.Put(IdxPrefix.MaxFileNo, String.valueOf(no).getBytes)
  }

  private def setFileFirstHeight(no: Int, height: Long) = {
    this.Put(IdxPrefix.FirstHeightOfFilePrefix + no + IdxPrefix.FirstHeightOfFileSuffix, String.valueOf(height).getBytes)
  }

  def getFileFirstHeight(no: Int): Long = {
    this.toLong(this.Get(IdxPrefix.FirstHeightOfFilePrefix + no + IdxPrefix.FirstHeightOfFileSuffix))
  }

  def rmFileFirstHeight(no: Int) {
    this.Delete(IdxPrefix.FirstHeightOfFilePrefix + no + IdxPrefix.FirstHeightOfFileSuffix)
  }

  ////////////////////以下是用来存储文件的////////////////////////////////////
  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	内部函数，读区块文件中的指定区块信息
   * @param	fileno Long 文件编号,startpos Long 区块信息存储的起始位置,length Int 读取数据的长度
   * @return	返回读取的区块字节数组
   */
  private def readBlock(fileno: Long, startpos: Long, length: Int): Array[Byte] = {
    val bs = filemgr.readBlock(fileno, startpos, length)
    bs
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	内部函数，写区块字节数组到指定文件的指定位置
   * @param	fileno Long 文件编号,startpos Long 区块信息存储的起始位置,bb Array[Byte] 区块字节数组
   * @return	如果写入成功返回true，否则false
   */
  private def writeBlock(fileno: Long, startpos: Long, bb: Array[Byte]): Boolean = {
    val b = filemgr.writeBlock(fileno, startpos, bb)
    b
  }
  /////////////////end////////////////////////////////////////////////////

}

////////////////以下半生对象用来做存储实例的管理，相当于存储管理的类工厂，每一个系统只能产生一个实例来进行操作///////////////
/**
 * @author jiangbuyun
 * @version	0.7
 * @since	2017-09-28
 * @category	ImpDataAccess类的伴生对象，用于单实例的生成。
 */
object ImpDataAccess {
  private var singleobjs = new scala.collection.mutable.HashMap[String, ImpDataAccess]()
  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	根据系统名称获取预执行的数据访问类
   * @param	SystemName String 系统名称
   * @return	如果成功返回ImpDataAccess实例，否则为null
   */
  def GetDataAccess(SystemName: String): ImpDataAccess = {
    var singleobj: ImpDataAccess = null
    synchronized {
      if (singleobjs.contains(SystemName)) {
        singleobj = singleobjs(SystemName)
      } else {
        singleobj = new ImpDataAccess(SystemName)
        singleobjs.put(SystemName, singleobj)
      }
      singleobj
    }
  }
}
////////////////end///////////////