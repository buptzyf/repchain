package rep.ssl

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.event.{Logging, MarkerLoggingAdapter}
import akka.remote.artery.tcp.ConfigSSLEngineProvider
import com.typesafe.config.Config
import javax.net.ssl._
import rep.storage.ImpDataAccess

/**
  * @author zyf
  * @param config
  * @param log
  */
class CustomSSLEngine(override val config: Config, override val log: MarkerLoggingAdapter)
  extends ConfigSSLEngineProvider(config: Config, log: MarkerLoggingAdapter) {

  def this(system: ActorSystem) = this(
    system.settings.config.getConfig("akka.remote.artery.ssl.config-ssl-engine"),
    Logging.withMarker(system, classOf[CustomSSLEngine].getName)
  )

  override val SSLTrustStore: String = config.getString("trust-net-store")

  /**
    * 使用自定义的TrustManager
    *
    * @see ReloadableTrustManager
    */
  override protected def trustManagers: Array[TrustManager] = {
    // 异步涉及到时序，如果没有等待，ImpDataAccess.GetDataAccess使用的leveldb路径是默认路径/users/jiangbuyun...，是错误的
    TimeUnit.SECONDS.sleep(5)
    // 检索leveldb用，这样可以直接获取到该节点的leveldb路径，并进行检索
    val dataAccess = ImpDataAccess.GetDataAccess(config.getString("node-name"))
    // 先设置为super_admin的账户
    val creditCode = config.getString("node-cert-prefix")
    Array(new ReloadableTrustManager(
      SSLTrustStore, // trustStore路径
      SSLTrustStorePassword, // trustStore的密码
      new TrustStoreDB(dataAccess, creditCode)) // leveldb中检索nodeCert会用到
    )
  }

}
