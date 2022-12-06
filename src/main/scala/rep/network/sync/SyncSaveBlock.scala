package rep.network.sync

import akka.actor.{ActorRef, Props}
import com.rcjava.client.ChainInfoClient
import com.rcjava.exception.SyncBlockException
import com.rcjava.protos.Peer
import com.rcjava.sync.{SyncInfo, SyncListener, SyncService}
import org.apache.http.conn.ssl.TrustSelfSignedStrategy
import org.apache.http.ssl.SSLContexts
import rep.log.RepLogger
import rep.network.base.ModuleBase
import rep.network.consensus.common.MsgOfConsensus.BlockRestore
import rep.network.module.ModuleActorType
import rep.network.persistence.IStorager.SourceOfBlock
import rep.storage.chain.block.BlockStorager
import rep.storage.chain.block.BlockStorager.BlockStoreResult

import java.io.File
import javax.net.ssl.SSLContext

object SyncSaveBlock {

  def props(name: String): Props = Props(new SyncSaveBlock(name))

}

class SyncSaveBlock(moduleName: String) extends ModuleBase(moduleName) with SyncListener {

  type Block2 = rep.proto.rc2.Block

  var locHeight: Long = 0
  var locBlkHash: String = ""
  var host: String = ""
  val store: BlockStorager = pe.getRepChainContext.getBlockStorager

  initSync()
  startSync()

  /**
   * 初始化同步
   */
  def initSync(): Unit = {
    val chainInfo = pe.getRepChainContext.getBlockSearch.getChainInfo
    locHeight = chainInfo.height
    locBlkHash = chainInfo.currentBlockHash.toStringUtf8
    host = pe.getRepChainContext.getConfig.getSyncHost
  }

  /**
   * 启动同步
   */
  def startSync(): Unit = {
    val syncInfo = new SyncInfo(locHeight, locBlkHash)
    val syncService = SyncService.newBuilder.setHost(host).setSyncInfo(syncInfo).setSyncListener(this).build()
    val sysConfig = pe.getRepChainContext.getConfig
    val syncSslConfig = sysConfig.getSyncSslConfig
    if (sysConfig.isSyncUseHttps) {
      val sslContext = SSLContexts.custom
        .loadTrustMaterial(
          new File(syncSslConfig.getString("trust-store")),
          syncSslConfig.getString("trust-store-password").toCharArray,
          new TrustSelfSignedStrategy)
        .loadKeyMaterial(
          new File(syncSslConfig.getString("key-store")),
          syncSslConfig.getString("key-store-password").toCharArray,
          syncSslConfig.getString("key-password").toCharArray)
        .build
      SyncService.newBuilder(syncService).setSslContext(sslContext).build().start()
    } else {
      syncService.start()
    }
  }


  /**
   * 1. 本地方法调用存储
   * 2. 发消息给存储Actor
   *
   * @param block 同步的区块
   */
  def onSuccess(block: Peer.Block): Unit = {
    // java -> scala
    val block2: Block2 = rep.proto.rc2.Block.parseFrom(block.toByteArray)
    val header = block2.getHeader
    RepLogger.info(RepLogger.BlockSyncher_Logger, s"同步区块成功，区块高度为：${header.height}")
    // 直接本地方法调用存储即可
    val result: BlockStoreResult = store.saveBlock(Some(block2))
    // 异步发给存储Actor
    // pe.getActorRef(ModuleActorType.ActorType.storager) ! BlockRestore(block2, SourceOfBlock.SYNC_BLOCK, ActorRef.noSender)
    if (result.isSuccess) {
      RepLogger.info(RepLogger.Storager_Logger, s"存储区块成功，区块高度为：${header.height}")
    } else {
      throw new SyncBlockException(s"Restore blocks error, save block info: height = ${header.height}, prehash=${header.hashPrevious.toStringUtf8}, currenthash=${header.hashPresent.toStringUtf8}")
    }
  }

  /**
   * 接收异常消息
   *
   * @param syncBlockException
   */
  def onError(syncBlockException: SyncBlockException): Unit = {
    RepLogger.except(RepLogger.BlockSyncher_Logger, s"同步区块异常，${syncBlockException.getMessage}", syncBlockException)
  }

  // 这里不用管，就把Actor当成一个线程就好了
  def receive: Receive = {
    null
  }

}
