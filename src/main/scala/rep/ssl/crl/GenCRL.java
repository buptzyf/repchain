package rep.ssl.crl;

import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter;
import org.bouncycastle.cert.jcajce.JcaX509v2CRLBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.Writer;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.Date;

import static org.apache.commons.lang3.time.DateUtils.MILLIS_PER_DAY;

public class GenCRL {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static void main(String[] args) throws Exception {

        KeyStore caKs = KeyStore.getInstance("JKS");
        caKs.load(new FileInputStream("jks/trust.jks"), "changeit".toCharArray());
        // 准备好创建 CRL 所需的私钥和证书
        PrivateKey caPrivateKey = (PrivateKey) caKs.getKey("trust", "changeit".toCharArray());
        X509Certificate caCertificate = (X509Certificate) caKs.getCertificate("trust");

        X509v2CRLBuilder crlBuilder = new JcaX509v2CRLBuilder(caCertificate, new Date());
//        crlBuilder.addCRLEntry(new BigInteger("1617115642"), new Date(), CRLReason.unspecified);

        // 7 天有效期
        crlBuilder.setNextUpdate(new Date(System.currentTimeMillis() + 7 * MILLIS_PER_DAY));
        JcaContentSignerBuilder contentSignerBuilder = new JcaContentSignerBuilder("SHA256WithECDSA").setProvider("BC");
        X509CRLHolder crlHolder = crlBuilder.build(contentSignerBuilder.build(caPrivateKey));
        JcaX509CRLConverter converter = new JcaX509CRLConverter().setProvider("BC");
        X509CRL x509CRL = converter.getCRL(crlHolder);

        Writer writer = new FileWriter("jks/" + "trust-init" + ".crl");
//        Writer writer = new FileWriter("jks/" + "trust_revoke3" + ".crl");
        JcaPEMWriter pemWriter = new JcaPEMWriter(writer);
        pemWriter.writeObject(x509CRL);
        pemWriter.close();
        writer.close();
    }

}
