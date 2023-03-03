package rep.crypto.ssl;

import javax.net.ssl.*;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public class gmssl_common {

    public static KeyStore loadKeystore(String  filename, String password)  {
        KeyStore keyStore = null;
        InputStream fin = null;
        try{
            keyStore = KeyStore.getInstance("PKCS12","GMJCE");
            fin = Files.newInputStream(Paths.get(filename));
            keyStore.load(fin, password.toCharArray());
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            try{
             if(fin != null)   fin.close();
            }catch (Exception el){}
        }

        return keyStore;
    }

    public static KeyManager[] keyManagers(String fname,String pwd) {
        KeyManagerFactory factory = null;
        try{
            factory = KeyManagerFactory.getInstance("SunX509");
            factory.init(loadKeystore(fname, pwd), pwd.toCharArray());
        }catch (Exception e){
            e.printStackTrace();
        }

        return factory.getKeyManagers();
    }


    public static TrustManager[] trustManagers(String fname,String pwd) {
        TrustManager[] trust = { new TrustAllManager() };
        /*TrustManagerFactory trustManagerFactory = null;
        try{
            trustManagerFactory = TrustManagerFactory.getInstance("SunX509");
            trustManagerFactory.init(loadKeystore(fname, pwd));
        }catch (Exception e){
            e.printStackTrace();
        }

        return trustManagerFactory.getTrustManagers();*/
        return trust;
    }

    public static SSLContext getGMSSLContext(String kname,String kpwd,String tname,String tpwd){
        SSLContext ctx = null;
        try {
            SecureRandom rng = new SecureRandom();
            ctx = SSLContext.getInstance("GMSSLv1.1", "GMJSSE");
            KeyManager[] keyManagers = keyManagers(kname,kpwd);
            TrustManager[] trustManagers = trustManagers(tname,tpwd);
            ctx.init(keyManagers, trustManagers(tname,tpwd), rng);
        } catch(Exception e){
            e.printStackTrace();
        }
        return ctx;
    }

    public static SSLEngine getGMSSLEngine(SSLContext ctx,String hostname,int port,boolean isClient){
        SSLEngine engine = ctx.createSSLEngine(hostname, port);

        if (isClient) {
            SSLParameters sslParams = ctx.getDefaultSSLParameters();
            sslParams.setEndpointIdentificationAlgorithm("HTTPS");
            engine.setSSLParameters(sslParams);
        }

        engine.setUseClientMode(isClient);
        if(isClient){
            engine.setEnabledCipherSuites(new String[]{"ECDHE_SM4_GCM_SM3"});
        }else{
            engine.setEnabledCipherSuites(engine.getSupportedCipherSuites());
        }
        engine.setEnabledProtocols(new String[]{"GMSSLv1.1"});

        return engine;
    }
}
