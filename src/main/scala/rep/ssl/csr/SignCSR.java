package rep.ssl.csr;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.*;
import java.util.Arrays;
import java.util.Date;

import static org.apache.commons.lang3.time.DateUtils.MILLIS_PER_DAY;


/**
 * CA 读取csr文件，并基于CSR生成证书（不包含ca证书）以及p7r文件（证书链）
 * CertificateChain，0 endCert， 1 interCaCert，2 rootCaCert
 * @see <a href=https://github.com/kaikramer/keystore-explorer/blob/eb9e0702452d8f7aef42e43c16c3980884880611/kse/src/org/kse/gui/actions/SignCsrAction.java#L197>
 *     generateCaReply</a>
 * @author
 */
public class SignCSR {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static void main(String[] args) {
        try {

            String dirPrefix = "jks/";
            String nodeName = "121000005l35120456.node1";

            // Certification Signing Request
            // PKCS#10
            PEMParser pemParser = new PEMParser(new FileReader(dirPrefix + nodeName + ".csr"));
            PKCS10CertificationRequest request = (PKCS10CertificationRequest) pemParser.readObject();

            // CA 密钥与证书读取
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(new FileInputStream(dirPrefix + "trust.jks"), "changeit".toCharArray());
            // The CA's private key
            PrivateKey privateKey = (PrivateKey) keyStore.getKey("trust", "changeit".toCharArray());
            //The CA's certificate as a X509Certificate
            X509Certificate caCert = (X509Certificate) keyStore.getCertificate("trust");

            //No of days the certificate should be valid
            int validity = 365 * 10;
            // a unique number
            String serialNo = "1617115640";
            // 签发日期
            Date issuedDate = new Date();
            // MILLIS_PER_DAY = 86400000l
            Date expiryDate = new Date(System.currentTimeMillis() + validity * MILLIS_PER_DAY);
            JcaPKCS10CertificationRequest jcaRequest = new JcaPKCS10CertificationRequest(request);
            jcaRequest.isSignatureValid(new JcaContentVerifierProviderBuilder().build(jcaRequest.getPublicKey()));
            X509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(caCert, new BigInteger(serialNo), issuedDate, expiryDate, jcaRequest.getSubject(), jcaRequest.getPublicKey());
            JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
            certificateBuilder
                    .addExtension(Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(caCert))
                    .addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(jcaRequest.getPublicKey()))
                    .addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment))
//                    .addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth))
//                    .addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth))
            ;

            ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").setProvider("BC").build(privateKey);

            //Add the CRL endpoint
            DistributionPointName crlEp = new DistributionPointName(new GeneralNames(new GeneralName(GeneralName.uniformResourceIdentifier, "http://127.0.0.1:8081/repchain.crl")));
            DistributionPoint disPoint = new DistributionPoint(crlEp, null, null);
            certificateBuilder.addExtension(Extension.cRLDistributionPoints, false, new CRLDistPoint(new DistributionPoint[]{disPoint}));

            //Add the OCSP endpoint
            AccessDescription ocsp = new AccessDescription(AccessDescription.id_ad_ocsp, new GeneralName(GeneralName.uniformResourceIdentifier, "http://127.0.0.1:8081/ocsp"));
            ASN1EncodableVector authInfoAccessASN = new ASN1EncodableVector();
            authInfoAccessASN.add(ocsp);
            certificateBuilder.addExtension(Extension.authorityInfoAccess, false, new DERSequence(authInfoAccessASN));
            X509Certificate signedCert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(certificateBuilder.build(signer));

            Writer writer = new FileWriter(dirPrefix + nodeName + ".cer");
            JcaPEMWriter pemWriter = new JcaPEMWriter(writer);
            pemWriter.writeObject(signedCert);
            pemWriter.close();
            writer.close();

            // generate p7r (cert request response)
            X509Certificate[] certChain = new X509Certificate[2];
            certChain[0] = signedCert;
            certChain[1] = caCert;
            CertificateFactory cf = CertificateFactory.getInstance("x.509", "BC");
            CertPath cp = cf.generateCertPath(Arrays.asList(certChain));

            // PKCS#7
            byte[] p7r = cp.getEncoded("PKCS7");
            FileOutputStream fios = new FileOutputStream(new File(dirPrefix + nodeName + ".p7r"));
            fios.write(p7r);
            // PEM - certChain
            byte[] pemBytes = cp.getEncoded("PEM");
            String pemString = new String(pemBytes);
            Files.writeString(new File(dirPrefix + nodeName + "_chain.cer").toPath(), pemString, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new RuntimeException("Error in signing the certificate", e);
        }

    }
}
