package rep.storage.verify

import rep.storage.ImpDataAccess
import rep.log.RepLogger
import rep.protos.peer._
import rep.crypto.Sha256
import scala.util.control.Breaks._
import rep.network.consensus.util.BlockHelp

object verify4Storage {
  def verify(sysName:String):Boolean={
    var b = true
    RepLogger.info(RepLogger.System_Logger,   "系统开始自检区块文件")
    var errorInfo = "未知问题"
    try{
      val sr: ImpDataAccess = ImpDataAccess.GetDataAccess(sysName)
      val bcinfo = sr.getBlockChainInfo()
      if(bcinfo != null){
        if(bcinfo.height > 0){
          var prehash = ""
          breakable(
          for(i <- 1 to bcinfo.height.toInt){
            val block = sr.getBlock4ObjectByHeight(i)
            if(block == null){
              errorInfo = "第"+i+"块信息无法获取，区块文件可能损坏。"
              b = false
              break
            }else{
              if(!prehash.equalsIgnoreCase("")){
                val bstr = block.previousBlockHash.toStringUtf8()
                if(!prehash.equals(bstr)){
                  errorInfo = "第"+i+"块信息错误，区块文件可能被篡改。"
                  b = false
                  break
                }
              }
              prehash = BlockHelp.GetBlockHash(block);
            }
          })
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
    RepLogger.info(RepLogger.System_Logger,  "系统自检区块文件完成")
    b
  }
  
}