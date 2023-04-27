package rep.app.conf.consensus

import rep.app.conf.consensus.ConsensusParameterConfig.{blockNumberKey, blockSizeKey, endorsementKey}
import rep.app.system.RepChainSystemContext
import rep.storage.chain.KeyPrefixManager
import rep.storage.db.factory.DBFactory
import rep.utils.SerializeUtils
import java.util.concurrent.atomic.AtomicInteger

object ConsensusParameterConfig{
  val endorsementKey = "db-cfrd-endorsement-strategy"
  val blockSizeKey = "db-cfrd-block-size"
  val blockNumberKey = "db-cfrd-blocker-rotation-frequency"
}

class ConsensusParameterConfig(ctx:RepChainSystemContext) {


  //每个出块人连续出块的数量，默认情况是1
  private var numberOfBlocksProduced = new AtomicInteger(InitParameter(
    "system.consensus.block_number_of_raft",blockNumberKey))
  //每个区块的大小，默认为2.4M，最大可以设置为5M
  private var blockSize = new AtomicInteger(InitParameter(
    "system.block.block_length", blockSizeKey))
  //背书的策略，默认是大于1/2；可以调整为大于2/3.
  private var endorsementStrategy = new AtomicInteger(InitParameter(
    "system.number_of_endorsement", endorsementKey))

  def getBlockSize:Int={
    this.blockSize.get()
  }

  def getEndorsementStrategy:Int={
    this.endorsementStrategy.get()
  }

  def getBlockNumberOfPer:Int={
    this.numberOfBlocksProduced.get()
  }

  def setBlockNumberOfPer(v:String):Unit={
    try{
      val num = Integer.parseInt(v)
      if(num >= 1){
        this.numberOfBlocksProduced.set(num)
      }
    }catch {
      case e:Exception=>e.printStackTrace()
    }
  }

  def setBlockSize(v: String): Unit = {
    try {
      val num = Integer.parseInt(v)
      this.blockSize.set(num)
    } catch {
      case e: Exception => e.printStackTrace()
    }
  }

  def setEndorsementStrategy(v: String): Unit = {
    try {
      val num = Integer.parseInt(v)
      if(num==2 || num == 3)
      this.endorsementStrategy.set(num)
    } catch {
      case e: Exception => e.printStackTrace()
    }
  }

  private def InitParameter(configKey:String,dbKey:String):Int = {
    val db = DBFactory.getDBAccess(ctx.getConfig)
    val keyValue = db.getObject[String](
      KeyPrefixManager.getWorldStateKey(ctx.getConfig, dbKey,
        ctx.getConfig.getConsensusParameterContractName))
    val bs = if(keyValue == None){
      val tmp = this.ctx.getConfig.getSystemConf.getInt(configKey)
      db.putBytes(KeyPrefixManager.getWorldStateKey(ctx.getConfig, dbKey,
        ctx.getConfig.getConsensusParameterContractName), SerializeUtils.serialise(tmp.toString))
      tmp
    }else{
      Integer.parseInt(keyValue.get)
    }
    bs
  }

}
