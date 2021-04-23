[TOC]

### [术语](https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/JSSERefGuide.html#Terms)

**key manager/trust manager**

> Key managers and trust managers use keystores for their key material. A key manager manages a keystore and supplies public keys to others as needed (for example, for use in authenticating the user to others). A trust manager decides who to trust based on information in the truststore it manages.
>
> 1. <font color=#ff00>管理`keystore`或`truststore`</font>
> 2. 用来初始化`SSLContext`

**keystore/truststore**

> A keystore is a database of key material. Key material is used for a variety of purposes, including authentication and data integrity. Various types of keystores are available, including PKCS12 and Oracle's JKS.
>
> Generally speaking, keystore information can be grouped into two categories: key entries and trusted certificate entries. A key entry consists of an entity's identity and its private key, and can be used for a variety of cryptographic purposes. In contrast, a trusted certificate entry contains only a public key in addition to the entity's identity. Thus, a trusted certificate entry cannot be used where a private key is required, such as in a `javax.net.ssl.KeyManager`. In the JDK implementation of JKS, a keystore may contain both key entries and trusted certificate entries.
>
> A truststore is a keystore that is used when making decisions about what to trust. If you receive data from an entity that you already trust, and if you can verify that the entity is the one that it claims to be, then you can assume that the data really came from that entity.
>
> An entry should only be added to a truststore if the user trusts that entity. By either generating a key pair or by importing a certificate, the user gives trust to that entry. Any entry in the truststore is considered a trusted entry.
>
> It may be useful to have two different keystore files: one containing just your key entries, and the other containing your trusted certificate entries, including CA certificates. The former contains private information, whereas the latter does not. Using two files instead of a single keystore file provides a cleaner separation of the logical distinction between your own certificates (and corresponding private keys) and others' certificates. To provide more protection for your private keys, store them in a keystore with restricted access, and provide the trusted certificates in a more publicly accessible keystore if needed.
>
> 1. <font color=#ff00><b>RepChain中`jks/***.node1.jks`（磁盘文件）与`keystore`（内存）对应，`jks/mytruststore.jks`（磁盘文件）于`truststore`（内存）对应</b></font>，`keystore`与`truststore`在建立TLS连接时会用到

### Load TrustKeyStore dynamically in RepChain

RepChain节点之间是使用 **TLS1.2**进行**P2P**通信的，默认初始网络有5个节点，对应于jks/mytruststore.jks中包含有5个节点的**自签名证书**，RepChain各个节点启动时，各自读取自己的mytruststore.jks，然后将信任证书列表写到内存（`truststore`）里

**缺点**：假设现在想要加入一个新的节点，各个在网的节点将新节点的证书导入到各自的mytruststore.jks中，这些节点在不重启的情况下，不会重新读取mytruststore.jks并加载该证书，那么新的节点与在网的各个节点就不能成功建立TLS连接

**粗暴的解决方法**：重启网络的所有节点，之后各个节点就会重新读取mytruststore.jks，并将新的包含新节点证书的列表写入到`truststore`中

**本文的目的**：解决<u>不重启网络</u>的情况下添加新节点，即让在网的节点可以热加载mytruststore.jks，进而可以与新节点建立TLS连接，最后新的节点可以加入到网络里

#### Akka TLS

在`application.conf`的关于**SSL**配置项里有两配置，分别是

* `config-ssl-engine`：相关配置项，jks文件地址与密码等
* `ssl-engine-provider = akka.remote.artery.tcp.ConfigSSLEngineProvider`
  * 加载`config-ssl-engine`配置项
  * 加载`config-ssl-engine.key-store`配置项中配置的文件（jks/***.node1.jks）来构建`keystore`
  * 加载`config-ssl-engine.trust-store`配置项中配置的文件（jks/mytruststore.jks）来构建`truststore`
  * 并初始化`SSLEngine`（节点间用来建立TLS链接）

akka中使用`ConfigSSLEngineProvider`来加载`keyStore`与`trustStore`等，然后创建[`sslContext`](https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/JSSERefGuide.html#SSLContext)并初始化[`SSLEngine`](https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/JSSERefGuide.html#SSLEngine)，上面提到节点启动时加载mytruststore.jks并将其中的可信任证书写到`truststore`就是在这个类里实现的，因此想要实现动态加载，需要从这里入手

**SSL相关初始化流程**：

1. `ConfigSSLEngineProvider`读取jks/***.node1.jks来构建`keystore`，读取jks/mytruststore.jks来构建`truststore`
2. `ConfigSSLEngineProvider`使用`truststore`来得到`trustmanager`，使用`keystore`得到`keymanager`
3. `ConfigSSLEngineProvider`使用`trustmanager`与`keymanager`来初始化得到`SSLContext`，使用`SSLContext`来得到`SSLEngine`

<font color=#FF00>**切入点**</font>：

1. `trustmanager`管理着`truststore`，想实现动态加载`truststore`，从`trustmanager`入手
2. `ConfigSSLEngineProvider`构建`trustmanager`，因此需要重载构建方法
3. 通过继承`ConfigSSLEngineProvider`并重载其中的`trustManagers`方法，可以将自定义的的`trustManager`传递进去
4. 自定义的`trustManager`中实现动态加载`truststore`

#### Custom TrustManager

* 实现要求有两点，分别是：
  * 可动态加载TrustStoreJksFile（存放各个节点自签名证书）
  * 如果TrustStoreJksFile不存在，则从leveldb中进行加载各个节点自签名证书（通过合约来初始化和更新节点证书）

* 先了解一下：[X509ExtendedTrustManager](https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/JSSERefGuide.html#X509ExtendedTrustManager)，其中有三个方法：

  * `getAcceptedIssuers()` 双向验证时，服务端发送给客户端CertificateRequest时，发送可信任证书的DN列表，<u>server hello时</u>发送
  * `checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)`  双向验证时服务端用来检查client证书，<u>server hello done</u>之后
  * `checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)` 客户端用来检查server证书，客户端接收到<u>server hello后</u>验证

  > 上述三个方法都是在**建立TLS连接时**会调用的，因此在任何一个方法中加载trustStore都可以，因为在网节点首先会作为server端，想要加入网络的节点首先会作为client，稍后才会互换角色。因此在网节点作为server端时，在先调用的getAcceptedIssuers()中加载trsutStore

* 在getAcceptedIssuers()中动态加载trustKeystore，涉及到两点，从jksFile中或者leveldb加载，<font color=#ff00>**<u>首先选择动态加载jksFile</u>**</font>

<font color=#FF00>**思路**</font>：

1. 继承`X509ExtendedTrustManager`，在方法`getAcceptedIssuers()`里加载`truststore`，这样每次建立TLS连接时，就会加载最新的`truststore`

   > 但是有个问题，怎么来验证`checkClientTrusted`与`checkServerTrusted`呢？最好是能调用到`X509ExtendedTrustManager`自带的验证方法

2. 那么包装一下，在自定义的`TrustManager`中，每次加载`truststore`时，都使用[`TrustManagerFactory`](https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/JSSERefGuide.html#TrustManagerFactory)与最新的`truststore`来构建一个`X509ExtendedTrustManager`实例，让这个实例来调用验证方法

具体代码：[ReloadableTrustManager](#ReloadableTrustManager)

#### Intro CustomTrustManager to AKKA

##### CustomSSLEngine

* 在`application.conf`中修改配置项：

  * `ssl-engine-provider = rep.ssl.CustomSSLEngine`（继承自ConfigSSLEngineProvider）

  * 新增自定义的配置项config-ssl-engine.trust-net-store

    > RepChain默认加载mytrustkeystore的证书验证签名，因此为了区分，引入新的配置项trust-net-store专门来负责组网
    
  * 新增自定义配置项node-name = "921000006e0012v696.node5"

    > 使用leveldb查询节点证书时，需要获得一个实例，需要给一个节点名，只能从配置文件中获取了
    >
    > ```scala
    > val dataAccess = ImpDataAccess.GetDataAccess(config.getString("node-name"))
    > ```

  * 新增自定义配置项node-cert-prefix = "951002007l78123233"

    > 将节点证书存储到leveldb中给一个前缀，当然也可以不给，合约中做相应处理即可

具体代码：[CustomSSLEngine](#CustomSSLEngine)

##### Smart Contract

管理各个节点证书（即信任列表），通过一个`map`来保存各个节点的证书，其实也可以通过`list`，使用`map`是为了方便对证书进行增删，如使用("121000005l35120456.node1" -> node1CertPem)来增加或更新或使用("121000005l35120456.node1" -> "")来删除，这样比`list`更容易进行更新

在自定义的`TrustManager`中，如果加载`truststore`时发现mytruststore.jks文件没了，那么会从leveldb中检索，然后使用保存有各个节点证书的`map`来构建`truststore`

具体代码：[SmartContract](#SmartContract)

### 参考文献

* Managing a Dynamic Java Trust Store：https://jcalcote.wordpress.com/2010/06/22/managing-a-dynamic-java-trust-store/
* Https的一个介绍：https://www.aneasystone.com/archives/2016/04/java-and-https.html
* Java Secure Socket Extension (JSSE) Reference Guide：https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/JSSERefGuide.html
  * X509ExtendedTrustManager and Creating own X509ExtendedTrustManager：https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/JSSERefGuide.html#X509ExtendedTrustManager
* JavaSE文档：https://docs.oracle.com/en/java/javase/index.html
* Tomcat热加载证书：https://my.oschina.net/u/157514/blog/395238
* [Dynamic certificate import to Trust Store with Java](https://darshanar.wordpress.com/2015/08/04/dynamic-certificate-import-to-trust-store-with-java-keytool/)
* Stackoverflow上的问题，implementing x509TrustManager
  * https://stackoverflow.com/questions/52487286/implementing-x509trustmanager
  * https://stackoverflow.com/questions/19005318/implementing-x509trustmanager-passing-on-part-of-the-verification-to-existing
* getAcceptedIssuers()
  * 如果是空列表会怎么样：https://stackoverflow.com/questions/15212263/proper-response-by-ssl-tls-client-for-certificate-request-message-with-no-dns/15223905#15223905
* TLS1.2 ietf 文档：
  * https://tools.ietf.org/html/rfc5246#section-7.4.2
  * https://tools.ietf.org/html/rfc6961
* TLS的一个流程剖析：https://segmentfault.com/a/1190000038316857
* SSL验证的一个基础知识：https://www.rabbitmq.com/ssl.html

### 附录

#### 调试TLS1.2

1. 配置SSL调试
   1. -Djavax.net.debug=ssl,handshake,verbose,session
2. 首先RepChain启动一个节点（加上调试配置项），假设为node1
3. 再启动一个节点（加上调试配置项），假设为node5
4. 二者之间建立连接时，会分别在各自终端打印出日志
5. 根据日志可分析SSL会话的建立流程

#### ReloadTrustManager

```java
package rep.ssl;

import rep.storage.ImpDataAccess;
import scala.collection.JavaConverters;
import scala.collection.mutable.HashMap;
import sun.security.validator.TrustStoreUtil;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.Socket;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.Set;

import static rep.storage.IdxPrefix.WorldStateKeyPreFix;
import static rep.utils.SerializeUtils.deserialise;


/**
 * 节点网络里，阻止新的节点进入可通过server端对client端做验证，因此server端(getAcceptedIssuers)重加载TrustManager即可满足,<p>
 * 当然也可在client端(CheckServerTrusted)重加载TrustManager
 *
 * @author zyf
 */
final public class ReloadableTrustManager extends X509ExtendedTrustManager {

    // 初始化TrustKeyStore的路径，“jks/mytruststore-net.jks”
    private String trustStorePath;
    // trustStore密码
    private String trustStorePassword;
    // 用来从levelDB上检索nodeCertMap，用来构造trustStore
    private TrustStoreDB trustStoreDB;
    // 默认不检查证书的有效时间
    private boolean checkValid = false;

    private KeyStore trustStore;
    //    private X509TrustManager trustManager;
    private X509ExtendedTrustManager trustManager;


    /**
     * @param tspath     trustStore初始化路径，“jks/mytruststore-net.jks”
     * @param tspassword trustStore密码
     * @param tsDB       用来从levelDB上检索nodeCertMap，用来构造trustStore
     * @throws Exception
     */
    public ReloadableTrustManager(String tspath, String tspassword, TrustStoreDB tsDB) throws Exception {
        this.trustStorePath = tspath;
        this.trustStorePassword = tspassword;
        this.trustStoreDB = tsDB;
        this.reloadTrustManager();
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        checkTrusted("client", chain, authType);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        checkTrusted("server", chain, authType);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
//        checkTrusted("client", chain, authType);
        trustManager.checkClientTrusted(chain, authType, socket);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
//        checkTrusted("server", chain, authType);
        trustManager.checkServerTrusted(chain, authType, socket);
    }

    /**
     * 该方法发生在<a href = https://tools.ietf.org/html/rfc5246#section-7.4.4>CertificateRequest</a>期间，属于Server端的操作<p>
     * 在{@link ReloadableTrustManager#checkClientTrusted}之前<p>
     * https://tools.ietf.org/html/rfc5246#section-7.4.4
     *
     * @return
     * @see sun.security.ssl.CertificateRequest
     */
    @Override
    public X509Certificate[] getAcceptedIssuers() {
        try {
            reloadTrustManager();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return trustManager.getAcceptedIssuers();
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        checkValid(chain);
//        trustManager.checkClientTrusted(chain, authType);
        trustManager.checkClientTrusted(chain, authType, engine);
        // TODO 程序执行到下面这一步的话，说明 check 通过
    }

    /**
     * @param chain
     * @param authType
     * @param engine
     * @throws CertificateException
     * @see sun.security.ssl.CertificateMessage.T12CertificateConsumer#consume
     */
    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        checkValid(chain);
//        trustManager.checkServerTrusted(chain, authType);
        trustManager.checkServerTrusted(chain, authType, engine);
        // TODO 程序执行到下面这一步的话，说明 check 通过
    }

    /**
     * @param role     标记是客户端还是server端
     * @param chain    the peer certificate chain
     * @param authType the key exchange algorithm used
     * @throws CertificateException
     */
    private void checkTrusted(String role, X509Certificate[] chain, String authType) throws CertificateException {
        System.out.println(role + " check " + chain.length);
        boolean result = false;
        try {
            if (chain == null || chain.length == 0 || authType == null || authType.length() == 0) {
                throw new IllegalArgumentException();
            }
            KeyStore store = reloadTrustKeyStore();
            for (X509Certificate certificate : chain) {
                if (checkValid) {
                    certificate.checkValidity();
                }
                String theAlias = store.getCertificateAlias(certificate);
                if (theAlias != null) {
                    result = true;
                }
            }
        } catch (Exception ex) {
            throw new CertificateException(ex.getMessage(), ex.getCause());
        }
        if (!result) {
            throw new CertificateException();
        }
    }

    /**
     * 检查证书的有效性
     *
     * @param chain
     * @throws CertificateException
     */
    private void checkValid(X509Certificate[] chain) throws CertificateException {
        try {
            if (checkValid) {
                for (X509Certificate certificate : chain) {
                    certificate.checkValidity();
                }
            }
        } catch (Exception ex) {
            throw new CertificateException(ex.getMessage(), ex.getCause());
        }
    }

    /**
     * reload trustStore，并获取其中的证书
     *
     * @return
     */
    private X509Certificate[] getX509CertsFromTrustKeyStore() {
        Set<X509Certificate> trustCertificates = new HashSet<>();
        try {
            KeyStore trustStore = reloadTrustKeyStore();
            trustCertificates = TrustStoreUtil.getTrustedCerts(trustStore);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        X509Certificate[] trustsArr = trustCertificates.toArray(new X509Certificate[0]);
        System.out.println("return trust array " + trustsArr.length);
        return trustsArr;
    }

    /**
     * 重新构建TrustStoreManager，调用该方法的话，会默认调用reloadTrustKeyStore，无需同时调用这两个方法
     *
     * @throws Exception
     */
    private void reloadTrustManager() throws Exception {

        // load keystore from specified cert store (or default)
        KeyStore store = reloadTrustKeyStore();

        // initialize a new TMF with the ts we just loaded
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(store);

        // acquire X509 trust manager from factory
        TrustManager tm[] = tmf.getTrustManagers();
        for (TrustManager manager : tm) {
//            if (manager instanceof X509TrustManager) {
//                 this.trustManager = (X509TrustManager) manager;
            if (manager instanceof X509ExtendedTrustManager) {
                this.trustManager = (X509ExtendedTrustManager) manager;
                return;
            }
        }

        throw new NoSuchAlgorithmException("No X509TrustManager in TrustManagerFactory");
    }


    /**
     * 初始化使用“jks/trustStore.jks”导入，后面如果改文件删除，则从leveldb中进行检索
     * 使用检索到的nodeCertMap构造keyStore，并返回
     *
     * @return
     * @throws CertificateException
     */
    private KeyStore reloadTrustKeyStore() throws CertificateException {

        // load keystore from specified cert store (or default)
        KeyStore store;
        File trustStoreFile = new File(trustStorePath);
        // 或者使用File.exist来判断，如果jksFile被删除了，那么从levelDB中检索
        // 或者使用catch FileNotException 来做判断来判断
        try {
            if (trustStoreFile.exists()) {
                store = loadTrustKeyStoreFromFile(trustStoreFile);
            } else {
                store = loadTrustKeyStoreFromDB(trustStoreDB.impDataAccess);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new CertificateException(ex.getMessage(), ex.getCause());
        }
        this.trustStore = store;
        return store;

    }

    /**
     * 从trustStoreFile来构造keyStore
     *
     * @return
     * @throws Exception
     */
    private KeyStore loadTrustKeyStoreFromFile(File trustStoreFile) throws Exception {
        KeyStore store = KeyStore.getInstance(trustStoreFile, trustStorePassword.toCharArray());
        Set<X509Certificate> trustCertificates = TrustStoreUtil.getTrustedCerts(store);
        System.out.println("loadTrustKeyStoreFromFile" + " ********************************** " + trustCertificates.size());
        return store;
    }

    /**
     * 从leveldb中检索nodeCertMap，然后来构造keyStore
     *
     * @return
     * @throws Exception
     */
    private KeyStore loadTrustKeyStoreFromDB(ImpDataAccess impDataAccess) throws Exception {
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        String stKey = WorldStateKeyPreFix() + "ManageNodeCert" + "_" + "tsdb_" + trustStoreDB.creditCode;
        // 使用kryo反序列化
        Object map = deserialise(impDataAccess.Get(stKey));
        // 合约中使用了scala.collection.mutable.HashMap
        if (map instanceof HashMap) {
            @SuppressWarnings("unchecked")
            HashMap<String, byte[]> nodeCertMap = (HashMap) deserialise(impDataAccess.Get(stKey));
            store.load(null, null);
            JavaConverters.mutableMapAsJavaMap(nodeCertMap).forEach((alias, nodeCertBytes) -> {
                try {
                    Certificate nodeCert = certFactory.generateCertificate(new ByteArrayInputStream(nodeCertBytes));
                    store.setCertificateEntry(alias, nodeCert);
                } catch (KeyStoreException | CertificateException ex) {
                    ex.printStackTrace();
                }
            });
            System.out.println("loadTrustKeyStoreFromDB" + " ********************************** " + nodeCertMap.size());
        } else if (map instanceof scala.collection.immutable.HashMap) {
            // blank
        } else if (map instanceof java.util.HashMap) {
            // blank
        } else {
            // blank
        }
        return store;
    }

    public String getTrustStorePath() {
        return trustStorePath;
    }

    public void setTrustStorePath(String trustStorePath) {
        this.trustStorePath = trustStorePath;
    }

    public TrustStoreDB getTrustStoreDB() {
        return trustStoreDB;
    }

    public void setTrustStoreDB(TrustStoreDB trustStoreDB) {
        this.trustStoreDB = trustStoreDB;
    }

    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    public void setTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    public boolean isCheckValid() {
        return checkValid;
    }

    public void setCheckValid(boolean checkValid) {
        this.checkValid = checkValid;
    }
}

/**
 * @author zyf
 */
class TrustStoreDB {

    // levelDB实例
    ImpDataAccess impDataAccess;
    // 相应的cert数据存放在哪个账户下
    String creditCode;

    public TrustStoreDB(ImpDataAccess impDataAccess, String creditCode) {
        this.impDataAccess = impDataAccess;
        this.creditCode = creditCode;
    }

    public ImpDataAccess getImpDataAccess() {
        return impDataAccess;
    }

    public void setImpDataAccess(ImpDataAccess impDataAccess) {
        this.impDataAccess = impDataAccess;
    }

    public String getCreditCode() {
        return creditCode;
    }

    public void setCreditCode(String creditCode) {
        this.creditCode = creditCode;
    }
}
```

#### CustomSSLEngine

```scala
package rep.ssl

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.event.{Logging, MarkerLoggingAdapter}
import akka.remote.artery.tcp.ConfigSSLEngineProvider
import com.typesafe.config.Config
import javax.net.ssl._
import rep.ssl.{CustomSSLEngine, ReloadableTrustManager}
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

```



#### SmartContract

```scala
package rep.sc.tpl

import java.io.StringReader

import org.bouncycastle.util.io.pem.PemReader
import org.json4s._
import org.json4s.jackson.JsonMethods._
import rep.protos.peer.ActionResult
import rep.sc.scalax.{ContractContext, ContractException, IContract}

import scala.collection.mutable.HashMap


/**
  * @author zyf
  */
class ManageNodeCert extends IContract {

  val prefix = "tsdb_"

  def init(ctx: ContractContext) {
    println(s"tid: ${ctx.t.id}, execute the contract which name is ${ctx.t.getCid.chaincodeName} and version is ${ctx.t.getCid.version}")
  }

  /**
    * 只能初始化一次
    *
    * @param ctx
    * @param data (节点名 -> 证书pem字符串)
    * @return
    */
  def initNodeCert(ctx: ContractContext, data: Map[String, String]): ActionResult = {
    val certKey = prefix + ctx.t.getSignature.getCertId.creditCode
    if (ctx.api.getVal(certKey) != null) {
      throw ContractException("已经初始化了，请使用updateNodeCert来更新")
    }
    // 初始化nodeCertMap
    val certMap = HashMap[String, Array[Byte]]()
    for ((alias, certPem) <- data) {
      val pemReader = new PemReader(new StringReader(certPem))
      val certBytes = pemReader.readPemObject().getContent
      certMap.put(alias, certBytes)
    }
    ctx.api.setVal(certKey, certMap)
    null
  }

  /**
    *
    * @param ctx
    * @param data (节点名 -> 证书pem字符串)，如果是(节点名 -> "")，则移除该节点的证书
    * @return
    */
  def updateNodeCert(ctx: ContractContext, data: Map[String, String]): ActionResult = {
    val certKey = prefix + ctx.t.getSignature.getCertId.creditCode
    if (ctx.api.getVal(certKey) == null) {
      throw ContractException("未始化了，请使用initNodeCert来初始化")
    }
    val certMap = ctx.api.getVal(certKey).asInstanceOf[HashMap[String, Array[Byte]]]
    for ((alias, certPem) <- data) {
      if (certPem.equals("")) {
        certMap.remove(alias)
      } else {
        val pemReader = new PemReader(new StringReader(certPem))
        val certBytes = pemReader.readPemObject().getContent
        certMap.put(alias, certBytes)
      }
    }
    ctx.api.setVal(certKey, certMap)
    null
  }

  /**
    * 根据action,找到对应的method，并将传入的json字符串parse为method需要的传入参数
    */
  def onAction(ctx: ContractContext, action: String, sdata: String): ActionResult = {

    implicit val formats = DefaultFormats
    val json = parse(sdata)

    action match {
      case "initNodeCert" =>
        initNodeCert(ctx, json.extract[Map[String, String]])
      case "updateNodeCert" =>
        updateNodeCert(ctx, json.extract[Map[String, String]])
    }
  }

}
```

