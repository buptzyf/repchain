package rep.crypto.nodedynamicmanagement

import akka.actor.ActorSystem
import akka.event.{Logging, MarkerLoggingAdapter}
import akka.remote.artery.tcp.ConfigSSLEngineProvider
import com.typesafe.config.Config
import javax.net.ssl._
import java.lang.reflect.Proxy

/**
 * @author zyf
 * @param config
 * @param log
 */
class CustomSSLEngine(override val config: Config, override val log: MarkerLoggingAdapter)
  extends ConfigSSLEngineProvider(config: Config, log: MarkerLoggingAdapter) {
  private var sysName = ""

  def this(system: ActorSystem) = {
    this(
      system.settings.config.getConfig("akka.remote.artery.ssl.config-ssl-engine"),
      Logging.withMarker(system, classOf[CustomSSLEngine].getName)
    )
    sysName = config.getString("node-name")
  }

  /**
   * 使用自定义的TrustManager
   *
   * @see ReloadableTrustManager
   */
  override protected def trustManagers: Array[TrustManager] = {
    Array(
      ReloadableTrustManager.getReloadableTrustManager(sysName).getProxyInstance
    )
  }

}
