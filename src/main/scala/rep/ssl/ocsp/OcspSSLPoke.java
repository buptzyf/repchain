package rep.ssl.ocsp;

import java.security.*;
import java.io.*;
import java.security.cert.*;
import java.util.EnumSet;
import javax.net.ssl.*;


/**
 * https://stackoverflow.com/questions/38301283/java-ssl-certificate-revocation-checking
 * https://docs.oracle.com/javase/8/docs/api/index.html?javax/net/ssl/X509ExtendedTrustManager.html
 *
 * Use -Djava.security.debug=certpath to debug...
 */
public class OcspSSLPoke {

    public static void poke(SSLSocket sslsocket) throws Exception {
        InputStream in = sslsocket.getInputStream();
        OutputStream out = sslsocket.getOutputStream();
        out.write(1);
        while (in.available() > 0) {
            System.out.print(in.read());
        }
        System.out.println("Successfully connected");
    }

    public static SSLSocket createDefaultSocket(String hostname, int port) throws Exception {
        SSLSocketFactory sslsocketfactory = (SSLSocketFactory) SSLContext.getInstance("TLSv1.2").getDefault().getSocketFactory();
        return (SSLSocket) sslsocketfactory.createSocket(hostname, port);
    }

    public static SSLSocket createSocketWithPKIXValidator(String hostname, int port) throws Exception {
        // create a PKIXRevocationChecker with the options we want: URL, Options, certificate,...
        // think about what you want to be configurable
        CertPathBuilder cpb = CertPathBuilder.getInstance("PKIX");
        PKIXRevocationChecker rc = (PKIXRevocationChecker)cpb.getRevocationChecker();
        rc.setOptions(EnumSet.of(
//                PKIXRevocationChecker.Option.PREFER_CRLS,
                PKIXRevocationChecker.Option.NO_FALLBACK));
//        rc.setOcspResponder(new URI("http://statuse.digitalcertvalidation.com"));
//        rc.setOcspResponder(new URI("http://ocsp.dcocsp.cn"));
        // now create the trustore and so on to create the PKIX parameters
        KeyStore ts = KeyStore.getInstance("JKS");
        FileInputStream tfis = new FileInputStream("D:\\Program Files\\Java\\zulu13.28.11-ca-jdk13.0.1-win_x64\\lib\\security\\cacerts");
        ts.load(tfis, "changeit".toCharArray());
        // create the PKIX paramaters and add the PKIXRevocationChecker
        PKIXBuilderParameters pkixParams = new PKIXBuilderParameters(ts, new X509CertSelector());
//        pkixParams.addCertPathChecker(rc);
//        pkixParams.setRevocationEnabled(true);
        // now you can create the trust managers and so on
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(new CertPathTrustManagerParameters(pkixParams));
        KeyStore ts1 = KeyStore.getInstance("JKS");
        FileInputStream tfis1 = new FileInputStream("jks/121000005l35120456.node1.jks");
        ts1.load(tfis1, "123".toCharArray());
        kmf.init(ts1, "123".toCharArray());
        SSLContext ctx = SSLContext.getInstance("TLSv1.2");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        // get the socket factory and create the connection => SSLPoke example
        SSLSocketFactory sslsocketfactory = ctx.getSocketFactory();
        return (SSLSocket) sslsocketfactory.createSocket(hostname, port);
    }

    public static void main(String... args) throws Exception {
        SSLSocket sslsocket = createSocketWithPKIXValidator(args[0], Integer.parseInt(args[1]));
        poke(sslsocket);
        System.out.println("======================================================================");
        Thread.sleep(1000);
        sslsocket = createDefaultSocket(args[0], Integer.parseInt(args[1]));
        poke(sslsocket);
        System.out.println("======================================================================");
    }
}