package rep.ssl.csr;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import java.io.*;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

/**
 * 创建CSR
 *
 * @author zyf
 */
public class GenCSR {

    static String node1Name = "121000005l35120456.node1";
    static String node2Name = "12110107bi45jh675g.node2";
    static String node3Name = "122000002n00123567.node3";
    static String node4Name = "921000005k36123789.node4";
    static String node5Name = "921000006e0012v696.node5";

    static String password = "123";

    public static void main(String[] args) throws Exception {

        KeyStore nodeJks = KeyStore.getInstance(KeyStore.getDefaultType());
        nodeJks.load(new FileInputStream("jks/" + node1Name + ".jks"), password.toCharArray());

        X509Certificate nodeCert = (X509Certificate) nodeJks.getCertificate(node1Name);
        PrivateKey nodePrivateKey = (PrivateKey) nodeJks.getKey(node1Name, password.toCharArray());
//        PublicKey nodePublickey = nodeCert.getPublicKey();

        // way 1
        X509CertificateHolder nodeCertHolder = new X509CertificateHolder(nodeCert.getEncoded());
        PKCS10CertificationRequestBuilder certRequestBuilder = new PKCS10CertificationRequestBuilder(nodeCertHolder.getSubject(), nodeCertHolder.getSubjectPublicKeyInfo());

        // way 2
        SubjectPublicKeyInfo subjectPublicKeyInfo = genKPbyPrivateKey(nodePrivateKey).getPublicKeyInfo();
        PublicKey nodePublicKey_1 = new JcaPEMKeyConverter().getPublicKey(subjectPublicKeyInfo);
        PKCS10CertificationRequest certRequestWithKP = CreateWithKeyPair(node1Name, nodePrivateKey, nodePublicKey_1);

        // way 3
        JcaPKCS10CertificationRequestBuilder jcaCertRequestBuilder = new JcaPKCS10CertificationRequestBuilder(nodeCert.getSubjectX500Principal(), nodeCert.getPublicKey());
        PKCS10CertificationRequest certRequest = jcaCertRequestBuilder.build(new JcaContentSignerBuilder("SHA256withECDSA").build(nodePrivateKey));

        JcaPEMWriter jcaPemWriter = new JcaPEMWriter(new FileWriter("jks/" + node1Name + "_code.csr"));
        jcaPemWriter.writeObject(certRequest);
        jcaPemWriter.flush();
        jcaPemWriter.close();

    }

    /**
     * @param nodeName
     * @param privateKey
     * @param publicKey
     * @return
     */
    public static PKCS10CertificationRequest CreateWithKeyPair(String nodeName, PrivateKey privateKey, PublicKey publicKey) throws OperatorCreationException {

        X500NameBuilder x500NameBuilder = new X500NameBuilder(BCStyle.INSTANCE)
                .addRDN(BCStyle.CN, nodeName)
                .addRDN(BCStyle.OU, "repchain")
                .addRDN(BCStyle.O, "iscas");
        X500Name subject = x500NameBuilder.build();

        PKCS10CertificationRequestBuilder requestBuilder = new JcaPKCS10CertificationRequestBuilder(subject, publicKey);
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").build(privateKey);
        return requestBuilder.build(signer);

    }

    /**
     * 通过privateKey生成PEMKeyPair，进而可以获得publicKey
     *
     * @param privateKey PrivateKey实例
     * @return
     * @throws Exception
     */
    public static PEMKeyPair genKPbyPrivateKey(PrivateKey privateKey) throws Exception {

        StringWriter writer = new StringWriter();
        JcaPEMWriter pemWriter = new JcaPEMWriter(writer);
        pemWriter.writeObject(privateKey);
        pemWriter.flush();
        String privateKeyString = writer.toString();

        StringReader reader = new StringReader(privateKeyString);
        PEMParser pemParser = new PEMParser(reader);
        PEMKeyPair pemKeyPair = (PEMKeyPair) pemParser.readObject();
        return pemKeyPair;

    }
}
