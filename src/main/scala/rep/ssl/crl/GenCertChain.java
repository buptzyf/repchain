package rep.ssl.crl;

import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import java.io.*;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;


/**
 * 将certChain写入到一个带有私钥的nodeJKS里，为csr签发证书，然后将该证书与trust.cer组成certChain写入到nodeJks里
 */
public class GenCertChain {

    static String node1Name = "121000005l35120456.node1";
    static String node2Name = "12110107bi45jh675g.node2";
    static String node3Name = "122000002n00123567.node3";
    static String node4Name = "921000005k36123789.node4";
    static String node5Name = "921000006e0012v696.node5";

    public static void main(String[] args) throws Exception {

        X509Certificate[] x509CertificateChain = new X509Certificate[2];

        KeyStore endKs = KeyStore.getInstance("JKS");
        endKs.load(new FileInputStream("jks/" + node1Name + ".jks"), "123".toCharArray());
        PrivateKey privateKey = (PrivateKey) endKs.getKey(node1Name, "123".toCharArray());

        Reader reader = new FileReader("jks/" + node1Name + ".cer");
        PemReader pemReader = new PemReader(reader);
        PemObject pemObject = pemReader.readPemObject();
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        X509Certificate x509Certificate = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(pemObject.getContent()));
        x509CertificateChain[0] = x509Certificate;
        reader.close();

        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(new FileInputStream("jks/mytrust.jks"), "changeit".toCharArray());
        x509CertificateChain[1] = ((X509Certificate) endKs.getCertificate("trust"));

        endKs.setKeyEntry(node1Name, privateKey, "123".toCharArray(), x509CertificateChain);
        endKs.store(new FileOutputStream("jks/121000005l35120456.node1.jks"), "123".toCharArray());

    }
}
