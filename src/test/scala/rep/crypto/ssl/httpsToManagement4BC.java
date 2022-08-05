package rep.crypto.ssl;

import rep.crypto.cert.CertificateUtil;
import scala.collection.mutable.HashMap;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;

public class httpsToManagement4BC{
    public static void main(String[] args)
    {
    SSLSocketFactory fact = null;
    HttpsURLConnection conn = null;

    try
    {
        String urlStr = "https://localhost:7081/management/system/SystemStartup/215159697776981712.node1";
        //String urlStr = "https://localhost:9081/web/g1.html";

        Security.insertProviderAt((Provider)Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider").newInstance(), 1);
        Security.insertProviderAt((Provider)Class.forName("org.bouncycastle.jsse.provider.BouncyCastleJsseProvider").newInstance(), 2);

        String pfxfile = "pfx/identity-net/215159697776981712.node1.pfx";
        String pwd = "123";
        KeyStore pfx = KeyStore.getInstance("PKCS12","BC");
        pfx.load(new FileInputStream(pfxfile), pwd.toCharArray());

        fact = createSocketFactory(pfx, pwd.toCharArray());
        SSLSocketFactory fact2 = new CipherSuiteOfSSLSocketFactory2(fact);

        URL url = new URL(urlStr);
        conn = (HttpsURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setSSLSocketFactory(fact2);
        conn.setHostnameVerifier(new HostnameVerifier()
        {
            public boolean verify(String hostname, SSLSession session)
            {
                return true;
            }
        });

        conn.connect();

        InputStream input = conn.getInputStream();
        byte[] buffer = new byte[1024 * 4];
        int length = 0;
        while ((length = input.read(buffer)) != -1)
        {
            System.out.println(new String(buffer, 0, length));
        }
    }
    catch (Exception e)
    {
        e.printStackTrace();
    }
    finally
    {
        try
        {
            conn.disconnect();
        }
        catch (Exception e)
        {}
    }
}



/*
        public static SSLSocketFactory createSocketFactory(KeyStore kepair, char[] pwd) throws Exception
        {
            //TrustAllManager[] trust = { new TrustAllManager() };

            KeyManager[] kms = null;
            if (kepair != null)
            {
                KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
                kmf.init(kepair, pwd);
                kms = kmf.getKeyManagers();
            }

            HashMap<String, Certificate> certs = TrustLoad.loadTrustCertificateFromTrustFile(
                    "pfx/identity-net/mytruststore.pfx","changeme","BC");
            KeyStore ks = TrustLoad.loadTrustStores(certs);
            X509ExtendedTrustManager xtm = TrustLoad.loadTrustManager(ks);
            TrustManager[] trusts = new TrustManager[]{xtm};


            *//*TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("SunX509");
            KeyStore tkeyStore = KeyStore.getInstance("PKCS12","BC");
            InputStream fin = Files.newInputStream(Paths.get("pfx/identity-net/mytruststore.pfx"));
            try {
                tkeyStore.load(fin, "changeme".toCharArray());
            } finally{
                fin.close();
            }

            trustManagerFactory.init(tkeyStore);
            TrustManager[] trusts = trustManagerFactory.getTrustManagers();*//*


            SSLContext ctx = SSLContext.getInstance("GMSSLv1.1", "BCJSSE");
            java.security.SecureRandom secureRandom = new java.security.SecureRandom();
            ctx.init(kms, trusts, secureRandom);

            ctx.getServerSessionContext().setSessionCacheSize(8192);
            ctx.getServerSessionContext().setSessionTimeout(20000);

            SSLSocketFactory factory = ctx.getSocketFactory();
            return factory;
        }*/

    public static SSLSocketFactory createSocketFactory(KeyStore kepair, char[] pwd) throws Exception
    {
        //TrustAllManager[] trust = { new TrustAllManager() };

        KeyManager[] kms = null;
        if (kepair != null)
        {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");
            kmf.init(kepair, pwd);
            kms = kmf.getKeyManagers();
        }

        /*TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("SunX509");
        KeyStore tkeyStore = KeyStore.getInstance("PKCS12","BC");
        InputStream fin = Files.newInputStream(Paths.get("pfx/identity-net/mytruststore.pfx"));
        try {
            tkeyStore.load(fin, "changeme".toCharArray());
        } finally{
            fin.close();
        }

        trustManagerFactory.init(tkeyStore);
        TrustManager[] trusts = trustManagerFactory.getTrustManagers();*/

        HashMap<String, Certificate> certs = TrustLoad.loadTrustCertificateFromTrustFile("pfx/identity-net/mytruststore.pfx","changeme","BC");
        KeyStore ks = TrustLoad.loadTrustStores(certs);
        X509ExtendedTrustManager xtm = TrustLoad.loadTrustManager(ks);
        TrustManager[] trusts = new TrustManager[]{xtm};


        SSLContext ctx = SSLContext.getInstance("GMSSLv1.1", "BCJSSE");
        java.security.SecureRandom secureRandom = new java.security.SecureRandom();
        ctx.init(kms, trusts, secureRandom);

        ctx.getServerSessionContext().setSessionCacheSize(8192);
        ctx.getServerSessionContext().setSessionTimeout(3600);

        SSLSocketFactory factory = ctx.getSocketFactory();
        return factory;
    }

}

class CipherSuiteOfSSLSocketFactory2 extends SSLSocketFactory
{

    private static final String PREFERRED_CIPHER_SUITE = "GMSSL_ECC_SM4_SM3";
    private static final String PREFERRED_PROTOCOL = "GMSSLv1.1";

    private final SSLSocketFactory delegate;

    public CipherSuiteOfSSLSocketFactory2(SSLSocketFactory delegate)
    {

        this.delegate = delegate;
    }

    @Override
    public String[] getDefaultCipherSuites()
    {

        return setupPreferredDefaultCipherSuites(this.delegate);
    }

    @Override
    public String[] getSupportedCipherSuites()
    {

        return setupPreferredSupportedCipherSuites(this.delegate);
    }

    @Override
    public Socket createSocket(String arg0, int arg1) throws IOException, UnknownHostException
    {

        Socket socket = this.delegate.createSocket(arg0, arg1);
        String[] cipherSuites = setupPreferredDefaultCipherSuites(delegate);
        ((SSLSocket) socket).setEnabledCipherSuites(cipherSuites);
        ArrayList<String> protocolList = new ArrayList<String>(Arrays.asList(new String[] {PREFERRED_PROTOCOL}));
        ((SSLSocket) socket).setEnabledProtocols(protocolList.toArray(new String[protocolList.size()]));
        ((SSLSocket) socket).setNeedClientAuth(true);

        return socket;
    }

    @Override
    public Socket createSocket(InetAddress arg0, int arg1) throws IOException
    {

        Socket socket = this.delegate.createSocket(arg0, arg1);
        String[] cipherSuites = setupPreferredDefaultCipherSuites(delegate);
        ((SSLSocket) socket).setEnabledCipherSuites(cipherSuites);
        ArrayList<String> protocolList = new ArrayList<String>(Arrays.asList(new String[] {PREFERRED_PROTOCOL}));
        ((SSLSocket) socket).setEnabledProtocols(protocolList.toArray(new String[protocolList.size()]));
        ((SSLSocket) socket).setNeedClientAuth(true);

        return socket;
    }

    @Override
    public Socket createSocket(Socket arg0, String arg1, int arg2, boolean arg3) throws IOException
    {

        Socket socket = this.delegate.createSocket(arg0, arg1, arg2, arg3);
        String[] cipherSuites = setupPreferredDefaultCipherSuites(delegate);
        ((SSLSocket) socket).setEnabledCipherSuites(cipherSuites);
        ArrayList<String> protocolList = new ArrayList<String>(Arrays.asList(new String[] {PREFERRED_PROTOCOL}));
        ((SSLSocket) socket).setEnabledProtocols(protocolList.toArray(new String[protocolList.size()]));
        ((SSLSocket) socket).setNeedClientAuth(true);

        return socket;
    }

    @Override
    public Socket createSocket(String arg0, int arg1, InetAddress arg2, int arg3) throws IOException, UnknownHostException
    {

        Socket socket = this.delegate.createSocket(arg0, arg1, arg2, arg3);
        String[] cipherSuites = setupPreferredDefaultCipherSuites(delegate);
        ((SSLSocket) socket).setEnabledCipherSuites(cipherSuites);
        ArrayList<String> protocolList = new ArrayList<String>(Arrays.asList(new String[] {PREFERRED_PROTOCOL}));
        ((SSLSocket) socket).setEnabledProtocols(protocolList.toArray(new String[protocolList.size()]));
        ((SSLSocket) socket).setNeedClientAuth(true);

        return socket;
    }

    @Override
    public Socket createSocket(InetAddress arg0, int arg1, InetAddress arg2, int arg3) throws IOException
    {

        Socket socket = this.delegate.createSocket(arg0, arg1, arg2, arg3);
        String[] cipherSuites = setupPreferredDefaultCipherSuites(delegate);
        ((SSLSocket) socket).setEnabledCipherSuites(cipherSuites);
        ArrayList<String> protocolList = new ArrayList<String>(Arrays.asList(new String[] {PREFERRED_PROTOCOL}));
        ((SSLSocket) socket).setEnabledProtocols(protocolList.toArray(new String[protocolList.size()]));
        ((SSLSocket) socket).setNeedClientAuth(true);
        return socket;
    }

    private static String[] setupPreferredDefaultCipherSuites(SSLSocketFactory sslSocketFactory)
    {
        ArrayList<String> suitesList = new ArrayList<String>(Arrays.asList(new String[] {PREFERRED_CIPHER_SUITE}));
        return suitesList.toArray(new String[suitesList.size()]);
    }

    private static String[] setupPreferredSupportedCipherSuites(SSLSocketFactory sslSocketFactory)
    {
        ArrayList<String> suitesList = new ArrayList<String>(Arrays.asList(new String[] {PREFERRED_CIPHER_SUITE}));
        return suitesList.toArray(new String[suitesList.size()]);
    }
}

/*
class PreferredCipherSuiteSSLSocketFactory2 extends SSLSocketFactory
{

    private static final String PREFERRED_CIPHER_SUITE = "GMSSL_ECC_SM4_SM3";
    private static final String PREFERRED_PROTOCOL = "GMSSLv1.1";

    private final SSLSocketFactory delegate;

    public PreferredCipherSuiteSSLSocketFactory2(SSLSocketFactory delegate)
    {

        this.delegate = delegate;
    }

    @Override
    public String[] getDefaultCipherSuites()
    {

        return setupPreferredDefaultCipherSuites(this.delegate);
    }

    @Override
    public String[] getSupportedCipherSuites()
    {

        return setupPreferredSupportedCipherSuites(this.delegate);
    }

    @Override
    public Socket createSocket(String arg0, int arg1) throws IOException, UnknownHostException
    {

        Socket socket = this.delegate.createSocket(arg0, arg1);
        String[] cipherSuites = setupPreferredDefaultCipherSuites(delegate);
        ((SSLSocket) socket).setEnabledCipherSuites(cipherSuites);
        ArrayList<String> protocolList = new ArrayList<String>(Arrays.asList(new String[] {PREFERRED_PROTOCOL}));
        ((SSLSocket) socket).setEnabledProtocols(protocolList.toArray(new String[protocolList.size()]));
        ((SSLSocket) socket).setNeedClientAuth(true);

        return socket;
    }

    @Override
    public Socket createSocket(InetAddress arg0, int arg1) throws IOException
    {

        Socket socket = this.delegate.createSocket(arg0, arg1);
        String[] cipherSuites = setupPreferredDefaultCipherSuites(delegate);
        ((SSLSocket) socket).setEnabledCipherSuites(cipherSuites);
        ArrayList<String> protocolList = new ArrayList<String>(Arrays.asList(new String[] {PREFERRED_PROTOCOL}));
        ((SSLSocket) socket).setEnabledProtocols(protocolList.toArray(new String[protocolList.size()]));
        ((SSLSocket) socket).setNeedClientAuth(true);

        return socket;
    }

    @Override
    public Socket createSocket(Socket arg0, String arg1, int arg2, boolean arg3) throws IOException
    {

        Socket socket = this.delegate.createSocket(arg0, arg1, arg2, arg3);
        String[] cipherSuites = setupPreferredDefaultCipherSuites(delegate);
        ((SSLSocket) socket).setEnabledCipherSuites(cipherSuites);
        ArrayList<String> protocolList = new ArrayList<String>(Arrays.asList(new String[] {PREFERRED_PROTOCOL}));
        ((SSLSocket) socket).setEnabledProtocols(protocolList.toArray(new String[protocolList.size()]));
        ((SSLSocket) socket).setNeedClientAuth(true);

        return socket;
    }

    @Override
    public Socket createSocket(String arg0, int arg1, InetAddress arg2, int arg3) throws IOException, UnknownHostException
    {

        Socket socket = this.delegate.createSocket(arg0, arg1, arg2, arg3);
        String[] cipherSuites = setupPreferredDefaultCipherSuites(delegate);
        ((SSLSocket) socket).setEnabledCipherSuites(cipherSuites);
        ArrayList<String> protocolList = new ArrayList<String>(Arrays.asList(new String[] {PREFERRED_PROTOCOL}));
        ((SSLSocket) socket).setEnabledProtocols(protocolList.toArray(new String[protocolList.size()]));
        ((SSLSocket) socket).setNeedClientAuth(true);

        return socket;
    }

    @Override
    public Socket createSocket(InetAddress arg0, int arg1, InetAddress arg2, int arg3) throws IOException
    {

        Socket socket = this.delegate.createSocket(arg0, arg1, arg2, arg3);
        String[] cipherSuites = setupPreferredDefaultCipherSuites(delegate);
        ((SSLSocket) socket).setEnabledCipherSuites(cipherSuites);
        ArrayList<String> protocolList = new ArrayList<String>(Arrays.asList(new String[] {PREFERRED_PROTOCOL}));
        ((SSLSocket) socket).setEnabledProtocols(protocolList.toArray(new String[protocolList.size()]));
        ((SSLSocket) socket).setNeedClientAuth(true);

        return socket;
    }

    private static String[] setupPreferredDefaultCipherSuites(SSLSocketFactory sslSocketFactory)
    {
        ArrayList<String> suitesList = new ArrayList<String>(Arrays.asList(new String[] {PREFERRED_CIPHER_SUITE}));
        return suitesList.toArray(new String[suitesList.size()]);
    }

    private static String[] setupPreferredSupportedCipherSuites(SSLSocketFactory sslSocketFactory)
    {
        ArrayList<String> suitesList = new ArrayList<String>(Arrays.asList(new String[] {PREFERRED_CIPHER_SUITE}));
        return suitesList.toArray(new String[suitesList.size()]);
    }

}
*/
