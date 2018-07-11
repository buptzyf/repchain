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

package rep.storage

import scala.util.parsing.json._;
import rep.protos.peer._;
import rep.storage.leveldb._;
import rep.storage.cfg._;
import rep.crypto._;


/**
 * @author jiangbuyun
 * @version	0.7
 * @since	2017-09-28
 * */
class blockindex() {
    private var  blockNum      : String = "repchain_default";//系统中暂时没有这个内容
    private var  blockHeight   : Long = -1;
    private var  blockHash     : String = "";
    private var  blockPrevHash : String = "";
    private var  txids         : Array[String] = null;
    
    private var  BlockFileNo   : Int = 0;
    private var  BlockFilePos  : Long = 0;
    private var  BlockLength   : Int = 0;
    
    def InitBlockIndex(b : Block)={
      if(b != null){
        val rbb = b.toByteArray
        this.blockHash = Sha256.hashstr(rbb);
        //不能使用statehash作为block的hashid
        //this.blockHash = b.stateHash.toString("UTF-8");
        this.blockPrevHash = b.previousBlockHash.toString("UTF-8");
        val ts = b.transactions;
        if(ts != null && ts.length > 0){
          txids = new Array[String](ts.length);
          var i = 0;
          ts.foreach(f =>{
              txids(i) = f.txid;
              i += 1;
          })
        }
      }
    }
    
    def InitBlockIndex(ab : Array[Byte])={
      if(ab != null){
        val jstr = new String(ab,"UTF-8");
        if(jstr != null){
           var m:Map[String,Any] = JsonUtil.json2Map(jstr);
           this.blockNum = this.str4null(getAnyType(m,"blockNum"));
           this.blockHeight = str2Long(getAnyType(m,"blockHeight"));
           this.blockHash = this.str4null(getAnyType(m,"blockHash"));;
           this.blockPrevHash = this.str4null(getAnyType(m,"blockPrevHash"));;
           this.txids = this.str2Array(getAnyType(m,"txids"));
           this.BlockFileNo = str2Int(getAnyType(m,"BlockFileNo"));
           this.BlockFilePos = str2Long(getAnyType(m,"BlockFilePos"));;
           this.BlockLength = str2Int(getAnyType(m,"BlockLength"));;
        }
      }
    }
    
    def getAnyType(m:Map[String,Any],key:String):String={
      var rstr = "";
      if(m.contains(key)) {
        val mv = m(key);
        if(mv == null){
          rstr = "";
        }else{
          rstr = mv.toString();
        }
      }
      
      rstr;
    }
    
    
    
    def str2Int(str : String):Int={
      var ri = 0;
      if(str != null){
        if(!str.equalsIgnoreCase("") && !str.equalsIgnoreCase("null")){
          ri = Integer.parseInt(str);
        }
      }
      ri;
    }
    
    def str2Long(str : String):Long={
      var rl:Long = 0;
      if(str != null){
        if(!str.equalsIgnoreCase("") && !str.equalsIgnoreCase("null")){
          rl = java.lang.Long.parseLong(str);
        }
      }
      rl;
    }
    
    def str4null(str : String):String={
      var rs = "";
      if(str == null){
        rs = "";
      }else if(str.equalsIgnoreCase("null")){
        rs = "";
      }else{
        rs = str;
      }
      rs;
    }
    
    def str2Array(str : String):Array[String]={
      var ra : Array[String] = null;
      if(str != null && !str.equalsIgnoreCase("") && !str.equalsIgnoreCase("null")){
        ra = str.split(" ");
      }
      ra;
    }
    
    
    
    def toJsonStr():String={
       var rstr = ""; 
       val map = scala.collection.mutable.HashMap[String,Any]();
       map.put("blockNum", blockNum );
       map.put("blockHeight", String.valueOf(blockHeight));
       map.put("blockHash", blockHash);
       map.put("blockPrevHash", blockPrevHash);
       if(txids != null && txids.length > 0){
         val str = txids.mkString(" ");
         map.put("txids", str);
       }
       map.put("BlockFileNo", String.valueOf(BlockFileNo));
       map.put("BlockFilePos", String.valueOf(BlockFilePos));
       map.put("BlockLength", String.valueOf(BlockLength));
       rstr = JsonUtil.map2Json(map.toMap);
       rstr;
    }
    
    def toArrayByte():Array[Byte]={
      val rstr = toJsonStr();
      val rb = rstr.getBytes("UTF-8");
      rb;
    }
    
    def getBlockNum():String={
      this.blockNum;
    }
    def setBlockNum(s:String)={
      this.blockNum = s;
    }
    
    def getBlockHeight():Long={
      this.blockHeight;
    }
    def setBlockHeight(l:Long)={
      this.blockHeight = l;
    }
    
    def getBlockHash():String={
      this.blockHash;
    }
   
    def getBlockPrevHash():String={
      this.blockPrevHash;
    }
    
    def getTxIds():Array[String]={
      this.txids;
    }
    
    def getBlockFileNo():Int={
      this.BlockFileNo;
    }
    def setBlockFileNo(l:Int)={
      this.BlockFileNo = l;
    }
    
    
    def getBlockFilePos():Long={
      this.BlockFilePos;
    }
    def setBlockFilePos(l:Long)={
      this.BlockFilePos = l;
    }
    
    def getBlockLength():Int={
      this.BlockLength;
    }
    def setBlockLength(l:Int)={
      this.BlockLength = l;
    }
    
    
}