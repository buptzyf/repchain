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



