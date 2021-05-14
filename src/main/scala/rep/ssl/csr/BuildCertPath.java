package rep.ssl.csr;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.*;
import java.util.*;
import java.util.stream.Collectors;


/**
 * 集成了revocation的证书路径建立
 * 其实主要就是验证证书路径
 *
 * @author zyf
 */
public class BuildCertPath {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static void main(String[] args) throws Exception {

        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(new FileInputStream("jks/certpath-trustAnchor.jks"), "changeit".toCharArray());

        KeyStore keyStore_1 = KeyStore.getInstance("JKS");
        keyStore_1.load(new FileInputStream("jks/certpath-full.jks"), "123".toCharArray());

        ArrayList list = new ArrayList<Certificate>(1);
        Enumeration alias = keyStore_1.aliases();
        while (alias.hasMoreElements()) {
            list.add(keyStore_1.getCertificate((String) alias.nextElement()));
        }
        CertStore certStore = CertStore.getInstance("Collection", new CollectionCertStoreParameters(list));

        X509CertSelector x509CertSelector = new X509CertSelector();
//        X500Name x500Name = new X500NameBuilder(BCStyle.INSTANCE)
//                .addRDN(BCStyle.CN, "node1")
//                .addRDN(BCStyle.OU, "repchain")
//                .addRDN(BCStyle.O, "iscas")
//                .build();
//        x509CertSelector.setSubject(new X500Principal(x500Name.toString()));
        x509CertSelector.setSubject(((X509Certificate) keyStore_1.getCertificate("end")).getSubjectX500Principal().getEncoded());
        PKIXBuilderParameters certPathParameters = new PKIXBuilderParameters(keyStore, x509CertSelector);
        certPathParameters.addCertStore(certStore);

        CertPathBuilder certPathBuilder = CertPathBuilder.getInstance("PKIX");
        PKIXRevocationChecker checker = (PKIXRevocationChecker) certPathBuilder.getRevocationChecker();
        checker.setOptions(EnumSet.of(PKIXRevocationChecker.Option.SOFT_FAIL));
        certPathParameters.addCertPathChecker(checker);
        certPathParameters.setRevocationEnabled(false);

        PKIXCertPathBuilderResult result = (PKIXCertPathBuilderResult) certPathBuilder.build(certPathParameters);
        CertPath certPath = result.getCertPath();
        List certPathList = certPath.getCertificates();

        List<X509Certificate> certList = (List<X509Certificate>) certPathList.stream().map(cert -> (X509Certificate) cert).collect(Collectors.toList());
        certList.add(result.getTrustAnchor().getTrustedCert());
        byte[] p7 = CertificateFactory.getInstance("X509", "BC").generateCertPath(certList).getEncoded("PEM");
        System.out.println(new String(p7));

    }
}
