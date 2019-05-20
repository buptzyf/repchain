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

package rep.storage.verify

import rep.storage.ImpDataAccess
import rep.log.RepLogger
import rep.protos.peer._
import rep.crypto.Sha256
import scala.util.control.Breaks._
import rep.network.consensus.util.{BlockVerify,BlockHelp}
import scala.util.Random
import scala.util.control.Breaks._

object verify4Storage {
  
  private def getFileInfo(sr: ImpDataAccess,blockHeight:Long):Set[(Int,Long,Long)] = {
    val fno = sr.getMaxFileNo()
    val fls = new Array[(Int,Long,Long)](fno+1)
    var i : Int = 0
    while(i <= fno){
      val first = sr.getFileFirstHeight(i)
      var last = blockHeight
      if(i < fno){
        last = sr.getFileFirstHeight(i+1)
      }
      fls(i) = (i,first,last)
      i += 1
    }
    fls.toSet
  }
  
  private def verfiyFileForFileInfo(firstHeigh:Long,lastHeight:Long,sr: ImpDataAccess):Boolean={
     var r = true
     val seed = lastHeight-firstHeigh
     breakable(
     for(i<-0 to 9){
       val rseed = Random.nextLong()
       var h = Math.abs(rseed) % seed + firstHeigh
       if(!verfiyBlockOfFile(h,sr)){
         r = false
         break
       }
     })
     r
  }
  
  private def verfiyBlockOfFile(height:Long,sr: ImpDataAccess):Boolean={
    var r = false
    var start:Block = null
    var end:Block = null
    if(height > 1){
      start = sr.getBlock4ObjectByHeight(height-1)
    }
    end = sr.getBlock4ObjectByHeight(height)
    if(VerfiyBlock(end,sr.SystemName)){
      if(start != null){
        if(VerfiyBlock(start,sr.SystemName)){
          val prehash = BlockHelp.GetBlockHash(start)
          if(prehash == end.previousBlockHash.toStringUtf8()){
            r = true
          }
        }
      }else{
        r = true
      }
    }
    r
  }
  
  private def VerfiyBlock(block:Block,sysName:String):Boolean={
    var vr = false
    val r = BlockVerify.VerifyAllEndorseOfBlock(block, sysName)
    if(r._1){
      val hash = BlockHelp.GetBlockHash(block)
      if(hash == block.hashOfBlock.toStringUtf8()){
        vr = true
      }
    }
    vr
  }
  
  def verify(sysName:String):Boolean={
    var b = true
    RepLogger.info(RepLogger.System_Logger,   "系统开始自检区块文件")
    var errorInfo = "未知问题"
    if(sysName == "921000006e0012v696.node5"){
      println("921000006e0012v696.node5")
    }
    try{
      val sr: ImpDataAccess = ImpDataAccess.GetDataAccess(sysName)
      val bcinfo = sr.getBlockChainInfo()
      if(bcinfo != null){
        if(bcinfo.height > 1){
          val flist = getFileInfo(sr,bcinfo.height)
          breakable(
          flist.foreach(f=>{
            if(!verfiyFileForFileInfo(f._2,f._3,sr)){
              errorInfo = "系统自检错误：存储检查失败，LevelDB或者Block文件损坏，请与管理员联系!"
              b = false
              break
            }
          })
          )
        }else if(bcinfo.height == 1 && !VerfiyBlock(sr.getBlock4ObjectByHeight(1),sysName)){
            errorInfo = "系统自检错误：存储检查失败，LevelDB或者Block文件损坏，请与管理员联系!"
            b = false
        }
      }else{
        errorInfo = "无法获取链信息，LevelDB可能损坏。"
        b = false
      }
    }catch{
      case e:Exception =>{
        RepLogger.except(RepLogger.System_Logger,  "系统自检错误：存储检查失败，LevelDB或者Block文件损坏，请与管理员联系！错误原因="+errorInfo,e)
        throw new Exception("系统自检错误：存储检查失败，LevelDB或者Block文件损坏，请与管理员联系！错误信息："+errorInfo+",其他信息="+e.getMessage)
      }
    }
    if(b){
      RepLogger.info(RepLogger.System_Logger,  "系统自检区块文件完成,")
    }else{
      RepLogger.info(RepLogger.System_Logger,  s"系统自检区块文件完成,出现错误，错误信息=${errorInfo}")
    }
    
    b
  }
  
}