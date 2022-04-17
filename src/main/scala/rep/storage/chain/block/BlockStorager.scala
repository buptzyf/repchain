package rep.storage.chain.block

import rep.app.conf.RepChainConfig
import rep.authority.cache.authbind.ImpAuthBindToCert
import rep.authority.cache.authcache.ImpAuthorizeCache
import rep.authority.cache.certcache.ImpCertCache
import rep.authority.cache.opcache.ImpOperateCache
import rep.authority.cache.signercache.ImpSignerCache
import rep.crypto.cert.certCache
import rep.log.RepLogger
import rep.proto.rc2.{Block, Transaction}
import rep.sc.tpl.did.DidTplPrefix
import rep.storage.chain.KeyPrefixManager
import rep.storage.db.common.{ITransactionCallback}
import rep.storage.encrypt.{EncryptFactory, IEncrypt}
import rep.storage.filesystem.common.IFileWriter
import rep.storage.filesystem.factory.FileFactory
import rep.storage.util.pathUtil
import scala.collection.mutable
import scala.util.control.Breaks.{break, breakable}

/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-13
 * @category	区块存储，继承区块查询器，复用存储器的查询方法。
 * */
class BlockStorager private(systemName:String,isEncrypt:Boolean=false) extends BlockSearcher(systemName,isEncrypt) {
  //private val db : IDBAccess = DBFactory.getDBAccess(this.systemName)
  private val cipherTool:IEncrypt = EncryptFactory.getEncrypt
  private val blockFileMaxLength = RepChainConfig.getSystemConfig(this.systemName).getStorageBlockFileMaxLength * 1024 * 1024
  private var lastChainInfo : Option[KeyPrefixManager.ChainInfo] = None
  private val lock: Object = new Object()

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	获取链信息
   * @param
   * @return 返回Option[KeyPrefixManager.ChainInfo]
   * */
  private def getLastChainInfo:Option[KeyPrefixManager.ChainInfo]={
    val obj = this.db.getObject(KeyPrefixManager.getBlockInfoKey(systemName))
    obj match {
      case None => Some(KeyPrefixManager.ChainInfo(0,"","",0,0,0,0))
      case _ => obj.asInstanceOf[Option[KeyPrefixManager.ChainInfo]]
    }
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	提取区块中的worldstate更新内容
   * @param block:Option[Block] 待存储的区块
   * @return 返回mutable.HashMap[String,Any]
   * */
  private def getOperateLog(block:Option[Block]):mutable.HashMap[String,Any]={
    val hm = new mutable.HashMap[String,Any]()
    val trs = block.get.transactions
    val result = block.get.transactionResults
    if(!result.isEmpty){
      for(i:Int <- 0 to result.size-1){
        val r = result(i)
        val t = trs(i)
        //val chainCodeId = IdTool.getCid(t.getCid)
        //val oid = if(t.oid.isEmpty) "_" else t.oid.toString
        val accountContractName = RepChainConfig.getSystemConfig(systemName).getAccountContractName
        val certMethod = RepChainConfig.getSystemConfig(systemName).getAccountCertChangeMethod

        r.statesSet.foreach(f=>{
          val k = f._1
          val v = f._2
          //hm.put(KeyPrefixManager.getWorldStateKey(this.systemName,k,chainCodeId,oid),v.toByteArray)
          //在存储时已经不需要组合key，直接使用
          hm.put(k,v.toByteArray)
          if(t.getCid.chaincodeName.equalsIgnoreCase(accountContractName) &&
            t.`type` == Transaction.Type.CHAINCODE_INVOKE && t.para.ipt.get.function.equalsIgnoreCase(certMethod)){
            //证书修改
            certCache.CertStatusUpdate(k)
          }else if(t.getCid.chaincodeName.equalsIgnoreCase(accountContractName) &&
            t.`type` == Transaction.Type.CHAINCODE_INVOKE){
            //账户修改
            updateCache4Account(k,v.toByteArray)
          }
        })
      }
    }

    hm
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	更新账户数据
   * @param k:String,value:Array[Byte]
   * @return
   * */
  private def updateCache4Account(k:String,value:Array[Byte]):Unit={
    if(k.indexOf("_"+DidTplPrefix.operPrefix)>0){
      val opcache = ImpOperateCache.GetOperateCache(this.systemName)
      if(opcache != null)
        opcache.ChangeValue(k)
    }else if(k.indexOf("_"+DidTplPrefix.authPrefix)>0){
      val authcache = ImpAuthorizeCache.GetAuthorizeCache(this.systemName)
      if(authcache != null)
        authcache.ChangeValue(k)
    }else if(k.indexOf("_"+DidTplPrefix.signerPrefix)>0){
      val signercache = ImpSignerCache.GetSignerCache(this.systemName)
      if(signercache != null)
        signercache.ChangeValue(k)
    }else if(k.indexOf("_"+DidTplPrefix.certPrefix)>0) {
      val certcache1 = ImpCertCache.GetCertCache(this.systemName)
      if(certcache1 != null)
        certcache1.ChangeValue(k)
    }else if(k.indexOf("_"+DidTplPrefix.bindPrefix)>0) {
      val authbindcert = ImpAuthBindToCert.GetAuthBindToCertCache(this.systemName)
      if(authbindcert != null)
        authbindcert.ChangeValue(k)
    }
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	根据当前文件号与区块长度获取待存储的区块的文件号
   * @param currentFileNo:Int 当前文件号,bLength:Int 待保存的区块长度
   * @return 返回IFileWriter文件写入器
   * */
  private def getFileNo(currentFileNo:Int,bLength:Int):IFileWriter={
    val writer:IFileWriter = FileFactory.getWriter(this.systemName,currentFileNo)
    if(writer.getFileLength + bLength.toLong > this.blockFileMaxLength){
      val fNo = currentFileNo+1
      FileFactory.getWriter(this.systemName,fNo)
    }else{
      writer
    }
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	保存区块
   * @param block: Option[Block] 待保存的区块
   * @return 返回BlockStorager.BlockStoreResult保存的结果
   * */
  def saveBlock(block: Option[Block]): BlockStorager.BlockStoreResult = {
    block match {
      case None=>
        BlockStorager.BlockStoreResult(false, 0l, 0l, "", "","block data is null")
      case _=>
        block.get.header match {
          case None=>
            BlockStorager.BlockStoreResult(false, 0l, 0l, "","", "block hash or previous hash is empty")
          case _=>
            lock.synchronized{
              if (this.lastChainInfo == None) {
                this.lastChainInfo = this.getLastChainInfo
              }

              if (this.lastChainInfo.get.previousHash.equalsIgnoreCase(block.get.header.get.hashPrevious.toStringUtf8)) {
                if(this.db.transactionOperate(new ITransactionCallback {
                  override def callback: Boolean = {
                    var r = false
                    try {
                      val hm = getOperateLog(block)
                      val bIndex = new BlockIndex(block.get)
                      val bb = if (isEncrypt) cipherTool.encrypt(block.get.toByteArray) else block.get.toByteArray
                      val bLength = bb.length
                      val writer = getFileNo(lastChainInfo.get.maxFileNo, bLength + 8)
                      bIndex.setFileNo(writer.getFileNo)
                      bIndex.setLength(bLength)
                      bIndex.setFilePos(writer.getFileLength + 8)
                      if (writer.getFileLength == 0) {
                        hm.put(KeyPrefixManager.getBlockFileFirstHeightKey(systemName, bIndex.getFileNo), bIndex.getHeight)
                      }
                      val lastInfo = Some(KeyPrefixManager.ChainInfo(bIndex.getHeight, bIndex.getHash, bIndex.getPreHash,
                        lastChainInfo.get.txCount + bIndex.getTransactionSize, bIndex.getFileNo, bIndex.getFilePos, bIndex.getLength))
                      hm.put(KeyPrefixManager.getBlockInfoKey(systemName), lastInfo)

                      hm.put(KeyPrefixManager.getBlockIndexKey4Height(systemName, bIndex.getHeight), bIndex)
                      hm.put(KeyPrefixManager.getBlockHeightKey4Hash(systemName, bIndex.getHash), bIndex.getHeight)
                      bIndex.getTxIds.foreach(id => {
                        hm.put(KeyPrefixManager.getBlockHeightKey4TxId(systemName, id), bIndex.getHeight)
                      })

                      hm.foreach(d => {
                        db.putObject(d._1, d._2)
                      })
                      writer.writeData(bIndex.getFilePos - 8, pathUtil.longToByte(bLength) ++ bb)
                      lastChainInfo = lastInfo
                    } catch {
                      case e: Exception =>
                        RepLogger.error(RepLogger.Storager_Logger,s"saving block's(height=${block.get.getHeader.height}) " +
                          s"msg=${e.getCause}")
                        throw e
                    }
                    r
                  }
                })){
                  BlockStorager.BlockStoreResult(true, lastChainInfo.get.height, lastChainInfo.get.txCount,
                    lastChainInfo.get.bHash, lastChainInfo.get.previousHash,"")
                }else{
                  BlockStorager.BlockStoreResult(false, lastChainInfo.get.height, lastChainInfo.get.txCount,
                    lastChainInfo.get.bHash, lastChainInfo.get.previousHash,
                    s"saving block's height=${block.get.getHeader.height} failed")
                }
              } else {
                BlockStorager.BlockStoreResult(false, lastChainInfo.get.height, lastChainInfo.get.txCount,
                  lastChainInfo.get.bHash, lastChainInfo.get.previousHash,
                  s"saving block's(height=${block.get.getHeader.height}) " +
                    s"previous hash=${block.get.header.get.hashPrevious} not equal last block's hash")
              }
            }
        }
    }
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	回滚区块
   * @param h:Long 待回滚的区块高度，默认为最后的区块
   * @return
   * */
  private def rollback(h:Long):Unit = {

    val blockOfRollback = this.getBlockByHeight(h)

    val bIndexOfRollback = this.getBlockIndexByHeight(Some(h)).get
    val preIndex = this.getBlockIndexByHeight(Some(h-1)).get
    val tmpLastChainInfo = Some(KeyPrefixManager.ChainInfo(preIndex.getHeight, preIndex.getHash, preIndex.getPreHash,
      lastChainInfo.get.txCount - bIndexOfRollback.getTransactionSize, preIndex.getFileNo, preIndex.getFilePos, preIndex.getLength))
    this.db.putObject(KeyPrefixManager.getBlockInfoKey(systemName),tmpLastChainInfo)
    this.db.delete(KeyPrefixManager.getBlockIndexKey4Height(systemName, bIndexOfRollback.getHeight))
    this.db.delete(KeyPrefixManager.getBlockHeightKey4Hash(systemName, bIndexOfRollback.getHash))

    if(this.getBlockHeightInFileFirstBlockByFileNo(bIndexOfRollback.getFileNo).get == bIndexOfRollback.getHeight){
      this.db.delete(KeyPrefixManager.getBlockFileFirstHeightKey(systemName, bIndexOfRollback.getFileNo))
    }

    bIndexOfRollback.getTxIds.foreach(id => {
      this.db.delete(KeyPrefixManager.getBlockHeightKey4TxId(systemName, id))
    })

    rollbackOperateLog(blockOfRollback)

    val write = FileFactory.getWriter(this.systemName,bIndexOfRollback.getFileNo)
    write.deleteBytesFromFileTail(bIndexOfRollback.getLength + 8)

    this.lastChainInfo  = tmpLastChainInfo

  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	回滚区块的worldstate
   * @param block:Option[Block] 待回滚的区块
   * @return
   * */
  private def rollbackOperateLog(block:Option[Block]):Unit={
    val trs = block.get.transactions
    val result = block.get.transactionResults
    if(!result.isEmpty){
      for(i:Int <- 0 to result.size-1){
        val r = result(i)
        val t = trs(i)
        val accountContractName = RepChainConfig.getSystemConfig(systemName).getAccountContractName
        val certMethod = RepChainConfig.getSystemConfig(systemName).getAccountCertChangeMethod

        r.statesSet.foreach(f=>{
          val k = f._1
          val v = f._2
          //hm.put(KeyPrefixManager.getWorldStateKey(this.systemName,k,chainCodeId,oid),v.toByteArray)
          //在存储时已经不需要组合key，直接使用
          val old = r.statesGet.getOrElse(k,null)
          if(old == null){
            this.db.delete(k)
          }else{
            this.db.putObject(k,old)
          }

          if(t.getCid.chaincodeName.equalsIgnoreCase(accountContractName) &&
            t.`type` == Transaction.Type.CHAINCODE_INVOKE && t.para.ipt.get.function.equalsIgnoreCase(certMethod)){
            //证书修改
            certCache.CertStatusUpdate(k)
          }else if(t.getCid.chaincodeName.equalsIgnoreCase(accountContractName) &&
            t.`type` == Transaction.Type.CHAINCODE_INVOKE){
            //账户修改
            updateCache4Account(k,v.toByteArray)
          }
        })
      }
    }
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	回滚区块到指定的高度
   * @param toHeight: Long 需要回滚的高度
   * @return 返回true成功，否则false
   * */
  def rollbackToHeight(toHeight: Long): Boolean = {
    this.lock.synchronized{
      var bv = true
      val chainInfo = this.getChainInfo
      var loop: Long = chainInfo.height
      breakable(
        while (loop > toHeight) {
            if(this.db.transactionOperate(new ITransactionCallback {
              override def callback: Boolean = {
                try{
                  rollback(loop)
                  true
                }catch {
                  case e:Exception =>
                    RepLogger.error(
                      RepLogger.Storager_Logger,
                      "system_name=" + systemName + s"\t current rollback block happend error ,happend pos height=${loop},contract administrator!")
                    false
                }
              }
            })){
              loop -= 1
              RepLogger.trace(RepLogger.Storager_Logger,
                "system_name=" + this.getSystemName + s"\t  rollback block success ,rollback height=${loop}")
            }else{
              bv = false
              break
            }
        })
      bv
    }
  }
}


/**
* @author jiangbuyun
* @version	2.0
* @since	2022-04-13
* @category	区块存储器实例管理
* */
object BlockStorager{
  case class BlockStoreResult(isSuccess:Boolean,lastHeight:Long,transactionCount:Long,
                              blockHash:String,previousBlockHash:String,reason:String)
  private val DBStorageInstances = new mutable.HashMap[String, BlockStorager]()

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-10
   * @category	设置相同的存储只能装载一个存储器实例
   * @param	systemName String 系统名称
   * @return	如果成功返回BlockStorager实例，否则为null
   */
  def getBlockStorager(systemName:String): BlockStorager = {
    var instance: BlockStorager = null
    synchronized {
      val config = RepChainConfig.getSystemConfig(systemName)
      val key = (config.getStorageDBPath+config.getStorageDBName+
                  config.getStorageBlockFilePath+config.getStorageBlockFileName
                )
      if (DBStorageInstances.contains(key)) {
        instance = DBStorageInstances(key)
      } else {
        instance = new BlockStorager(systemName)
        DBStorageInstances.put(key, instance)
      }
      instance
    }
  }
}
