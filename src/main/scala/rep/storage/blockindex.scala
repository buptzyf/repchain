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

import scala.util.parsing.json._
import rep.proto.rc2._
import rep.storage.leveldb._
import rep.storage.cfg._
import rep.crypto._
import rep.log.RepLogger;

/**
 * @author jiangbuyun
 * @version	0.7
 * @since	2017-09-28
 */
class blockindex() {
  private var blockNum: String = "repchain_default" //系统中暂时没有这个内容
  private var blockHeight: Long = -1
  private var blockHash: String = ""
  private var blockPrevHash: String = ""
  private var stateHash: String = ""
  private var txids: Array[String] = null

  private var BlockFileNo: Int = 0
  private var BlockFilePos: Long = 0
  private var BlockLength: Int = 0

  private var createTime: String = ""
  private var createTimeUtc: String = ""

  private def ReadBlockTime(b: Block) = {
    if (b != null && b.header.get.endorsements != null && b.header.get.endorsements.length >= 1) {
      val signer = b.header.get.endorsements(0)
      val date = new java.util.Date(signer.tmLocal.get.seconds * 1000);
      val formatstr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      formatstr.setTimeZone(java.util.TimeZone.getTimeZone("ETC/GMT-8"))
      val tmpstr = formatstr.format(date)
      val millis = signer.tmLocal.get.nanos / 1000000
      this.createTime = tmpstr + "." + millis
      // 13位,毫秒精度级(utc时间)
      this.createTimeUtc = String.valueOf((signer.tmLocal.get.seconds * 1000 + millis) - 8 * 3600 * 1000)
    }
  }

  def InitBlockIndex(b: Block) = {
    if (b != null) {
      //val rbb = b.toByteArray
      //this.blockHash = Sha256.hashstr(rbb);
      //不能使用statehash作为block的hashid
      //this.blockHash = b.stateHash.toString("UTF-8");
      //this.blockHash = b.
      this.blockHeight = b.header.get.height
      this.blockHash = b.header.get.hashPresent.toStringUtf8()
      this.blockPrevHash = b.header.get.hashPrevious.toStringUtf8()
      this.ReadBlockTime(b)
      val ts = b.transactions
      if (ts != null && ts.length > 0) {
        txids = new Array[String](ts.length)
        var i = 0
        ts.foreach(f => {
          txids(i) = f.id
          i += 1
        })
      }
    }
  }

  def InitBlockIndex(ab: Array[Byte]) = {
    if (ab != null) {
      val jstr = new String(ab, "UTF-8")
      if (jstr != null) {

        var m: Map[String, Any] = JsonUtil.json2Map(jstr)
        this.blockNum = this.str4null(getAnyType(m, "blockNum"))
        this.blockHeight = str2Long(getAnyType(m, "blockHeight"))
        this.blockHash = this.str4null(getAnyType(m, "blockHash"))
        this.blockPrevHash = this.str4null(getAnyType(m, "blockPrevHash"))
        this.stateHash = this.str4null(getAnyType(m, "stateHash"))
        this.txids = this.str2Array(getAnyType(m, "txids"))
        this.BlockFileNo = str2Int(getAnyType(m, "BlockFileNo"))
        this.BlockFilePos = str2Long(getAnyType(m, "BlockFilePos"))
        this.BlockLength = str2Int(getAnyType(m, "BlockLength"))
        this.createTime = this.str4null(getAnyType(m, "createTime"))
        this.createTimeUtc = this.str4null(getAnyType(m, "createTimeUtc"))
      }
    }
  }

  private def getAnyType(m: Map[String, Any], key: String): String = {
    var rstr = ""
    if (m.contains(key)) {
      if (m(key) == null) {
        rstr = ""
      } else {
        rstr = m(key).toString()
      }
    }

    rstr
  }

  private def str2Int(str: String): Int = {
    var ri = 0
    if (str != null && !str.equalsIgnoreCase("") && !str.equalsIgnoreCase("null")) {
      ri = Integer.parseInt(str)
    }
    ri
  }

  private def str2Long(str: String): Long = {
    var rl: Long = 0
    if (str != null && !str.equalsIgnoreCase("") && !str.equalsIgnoreCase("null")) {
      rl = java.lang.Long.parseLong(str)
    }
    rl
  }

  private def str4null(str: String): String = {
    var rs = ""
    if (str == null || str.equalsIgnoreCase("null")) {
      rs = ""
    } else {
      rs = str
    }
    rs
  }

  private def str2Array(str: String): Array[String] = {
    var ra: Array[String] = null
    if (str != null && !str.equalsIgnoreCase("") && !str.equalsIgnoreCase("null")) {
      ra = str.split(" ")
    }
    ra
  }

  private def toJsonStr(): String = {
    var rstr = ""
    val map = scala.collection.mutable.HashMap[String, Any]()
    map.put("blockNum", blockNum)
    map.put("blockHeight", String.valueOf(blockHeight))
    map.put("blockHash", blockHash)
    map.put("blockPrevHash", blockPrevHash)
    map.put("stateHash", stateHash)
    if (txids != null && txids.length > 0) {
      val str = txids.mkString(" ")
      map.put("txids", str)
    }
    map.put("BlockFileNo", String.valueOf(BlockFileNo))
    map.put("BlockFilePos", String.valueOf(BlockFilePos))
    map.put("BlockLength", String.valueOf(BlockLength))
    map.put("createTime", this.createTime)
    map.put("createTimeUtc", this.createTimeUtc)
    rstr = JsonUtil.map2Json(map.toMap)
    rstr;
  }

  def toArrayByte(): Array[Byte] = {
    val rstr = toJsonStr()
    val rb = rstr.getBytes("UTF-8")
    rb
  }

  def getNumberOfTrans: Int = {
    var c: Int = 0
    if (this.txids != null) {
      c = this.txids.length
    }
    c
  }

  def getBlockNum(): String = {
    this.blockNum
  }
  def setBlockNum(s: String) = {
    this.blockNum = s
  }

  def getBlockHeight(): Long = {
    this.blockHeight
  }
  def setBlockHeight(l: Long) = {
    this.blockHeight = l
  }

  def getBlockHash(): String = {
    this.blockHash
  }

  def getBlockPrevHash(): String = {
    this.blockPrevHash
  }

  def getStateHash(): String = {
    this.stateHash
  }

  def getTxIds(): Array[String] = {
    this.txids
  }

  def getBlockFileNo(): Int = {
    this.BlockFileNo
  }
  def setBlockFileNo(l: Int) = {
    this.BlockFileNo = l
  }

  def getBlockFilePos(): Long = {
    this.BlockFilePos
  }
  def setBlockFilePos(l: Long) = {
    this.BlockFilePos = l
  }

  def getBlockLength(): Int = {
    this.BlockLength
  }
  def setBlockLength(l: Int) = {
    this.BlockLength = l
  }

  def getCreateTime: String = {
    this.createTime
  }

  def setCreateTime(b: Block) = {
    this.ReadBlockTime(b)
  }

  def getCreateTimeUtc: String = {
    this.createTimeUtc
  }

}