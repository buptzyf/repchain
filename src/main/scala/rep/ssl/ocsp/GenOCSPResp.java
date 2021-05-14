package rep.ssl.ocsp;

import com.alibaba.fastjson.JSONObject;
import com.rcjava.client.TranPostClient;
import com.rcjava.protos.Peer;
import com.rcjava.tran.TranCreator;
import com.rcjava.util.CertUtil;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.ocsp.*;
import org.bouncycastle.cert.ocsp.jcajce.JcaRespID;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.UUID;

import static org.apache.commons.lang3.time.DateUtils.MILLIS_PER_DAY;


/**
 * 生成<a href = "https://tools.ietf.org/html/rfc6960#section-4.4.7.2.2">Static OcspResonse</a>
 * <p>
 * <a href = "https://tools.ietf.org/html/rfc6960">OCSP</a>
 * <p>
 *
 * @author zyf
 */
public class GenOCSPResp {

    // 标识账户证书准备使用 <<普通用户>> 对应的私钥
    private static Peer.CertId certId = Peer.CertId.newBuilder().setCreditCode("951002007l78123233").setCertName("super_admin").build();
    // 获取私钥，实际业务中可以以不同方式保存privateKey，只要读进来能构造为PrivateKey对象即可
    // 获取私钥，这里使用jks，也可以使用pem方式来构建，标准的PrivateKey即可
    private static PrivateKey privateKey = CertUtil.genX509CertPrivateKey(new File(String.format("jks/951002007l78123233.super_admin.jks")), "super_admin",
            String.format("%s.%s", "951002007l78123233", "super_admin")).getPrivateKey();
    private static TranCreator tranCreator = TranCreator.newBuilder().setPrivateKey(privateKey).setSignAlgorithm("sha1withecdsa").build();
    private static Peer.ChaincodeId manageNodeCertId = Peer.ChaincodeId.newBuilder().setChaincodeName("ManageNodeCert").setVersion(1).build();

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    static BigInteger node1SerialNumber = new BigInteger("1617115640");
    static BigInteger node2SerialNumber = new BigInteger("1617115641");
    static BigInteger node3SerialNumber = new BigInteger("1617115642");
    static BigInteger node4SerialNumber = new BigInteger("1617115643");
    static BigInteger node5SerialNumber = new BigInteger("1617115644");

    static String node1Name = "121000005l35120456.node1";
    static String node2Name = "12110107bi45jh675g.node2";
    static String node3Name = "122000002n00123567.node3";
    static String node4Name = "921000005k36123789.node4";
    static String node5Name = "921000006e0012v696.node5";

    static String nodePassword = "123";

    static CertificateStatus nodeGoodStatus = CertificateStatus.GOOD;
    static CertificateStatus nodeReovokeStatus = new RevokedStatus(new Date(), CRLReason.certificateHold);


    public static void main(String[] args) throws Exception {

        String ocspRespPem = genNodeOcsp(node5SerialNumber, nodeGoodStatus);
//        System.out.println(node1SerialNumber.toString());
        String txid = updateOcspRespToDB(node5SerialNumber.toString(), ocspRespPem);
        System.out.println("***********" + txid);
    }

    /**
     * 生成证书的ocspResponse
     *
     * @param serialNumber
     * @param certificateStatus
     * @return
     * @throws Exception
     */
    public static String genNodeOcsp(BigInteger serialNumber, CertificateStatus certificateStatus) throws Exception {

        // 准备好创建 OCSP 所需的私钥和证书
        KeyStore caKs = KeyStore.getInstance("JKS");
        caKs.load(new FileInputStream("jks/trust.jks"), "changeit".toCharArray());
        X509Certificate caCertificate = (X509Certificate) caKs.getCertificate("trust");
        PrivateKey caPrivateKey = (PrivateKey) caKs.getKey("trust", "changeit".toCharArray());

        // 构建响应Id
        RespID respID = new JcaRespID(caCertificate.getSubjectX500Principal());
        BasicOCSPRespBuilder responseBuilder = new BasicOCSPRespBuilder(respID);
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").setProvider("BC").build(caPrivateKey);
        // Generate the id for the certificate we are looking for
        CertificateID certificateID = buildCertificateID(serialNumber);
        responseBuilder.addResponse(certificateID, certificateStatus, new Date(), new Date(System.currentTimeMillis() + 10 * 365 * MILLIS_PER_DAY), null);

        BasicOCSPResp ocspResponse = responseBuilder.build(signer, new X509CertificateHolder[]{new JcaX509CertificateHolder(caCertificate)}, new Date());

        OCSPRespBuilder ocspResponseBuilder = new OCSPRespBuilder();
        OCSPResp ocspResp = ocspResponseBuilder.build(OCSPRespBuilder.SUCCESSFUL, ocspResponse);

        // 使用十六进制进行保存传输
        String ocspRespHexString = Hex.encodeHexString(ocspResp.getEncoded());
        System.out.println(ocspRespHexString);
        OCSPResp rebackOcspResp = new OCSPResp(Hex.decodeHex(ocspRespHexString.toCharArray()));
        System.out.println("返回来了" + ((BasicOCSPResp) rebackOcspResp.getResponseObject()).getProducedAt());

        // 使用pem保存传输
        StringWriter stringWriter = new StringWriter();
        JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter);
        pemWriter.writeObject(new PemObject("OCSP RESPONSE", ocspResp.getEncoded()));
        pemWriter.close();
        String ocspRespPem = stringWriter.toString();
        System.err.println(ocspRespPem);
        StringReader stringReader = new StringReader(ocspRespPem);
        PemReader pemReader = new PemReader(stringReader);
        PemObject pemObject = pemReader.readPemObject();
        pemReader.close();
        OCSPResp rebackOcspRespPem = new OCSPResp(pemObject.getContent());
        System.err.println(rebackOcspRespPem);
        return ocspRespPem;
    }

    /**
     * 除了证书之外其他的OcspResponse响应
     *
     * @throws OCSPException
     */
    public static void genMalFormedOcsp() throws OCSPException {
        OCSPRespBuilder ocspResponseBuilder = new OCSPRespBuilder();
        ocspResponseBuilder.build(OCSPRespBuilder.MALFORMED_REQUEST, null);
    }

    /**
     * @param serialNumber
     * @return
     * @throws Exception
     */
    public static CertificateID buildCertificateID(BigInteger serialNumber) throws Exception {
        KeyStore nodeJks = KeyStore.getInstance(KeyStore.getDefaultType());
        nodeJks.load(new FileInputStream("jks/" + "mytrust" + ".jks"), "changeit".toCharArray());
        X509Certificate issuingCertificate = (X509Certificate) nodeJks.getCertificate("trust");
        // Generate the id for the certificate we are looking for
        CertificateID certificateID = new CertificateID(
                new JcaDigestCalculatorProviderBuilder().setProvider("BC").build().get(CertificateID.HASH_SHA1),
                new JcaX509CertificateHolder(issuingCertificate),
                serialNumber
        );
        return certificateID;
    }

    /**
     * @param serialNumber
     * @param ocspRespPem
     * @return
     */
    public static String updateOcspRespToDB(String serialNumber, String ocspRespPem) {
        String tranId = UUID.randomUUID().toString().replace("-", "");
        JSONObject json = new JSONObject();
        json.fluentPut("serialNumber", serialNumber);
        json.fluentPut("ocspRespPem", ocspRespPem);
        Peer.Transaction tran = tranCreator.createInvokeTran(tranId, certId, manageNodeCertId, "updateOcspRespToDB", json.toJSONString());
        JSONObject res = new TranPostClient("localhost:8081").postSignedTran(tran);
        System.err.println(res);
        return res.getString("txid");
    }

}
