package rep.ssl.crl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.rcjava.client.TranPostClient;
import com.rcjava.protos.Peer;
import com.rcjava.tran.TranCreator;
import com.rcjava.util.CertUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.util.UUID;

/**
 * 提交交易
 *
 * @author zyf
 */
public class CrlUtil {

    // 标识账户证书准备使用 <<普通用户>> 对应的私钥
    private static Peer.CertId certId = Peer.CertId.newBuilder().setCreditCode("951002007l78123233").setCertName("super_admin").build();
    // 获取私钥，实际业务中可以以不同方式保存privateKey，只要读进来能构造为PrivateKey对象即可
    // 获取私钥，这里使用jks，也可以使用pem方式来构建，标准的PrivateKey即可
    private static PrivateKey privateKey = CertUtil.genX509CertPrivateKey(new File(String.format("jks/951002007l78123233.super_admin.jks")), "super_admin",
            String.format("%s.%s", "951002007l78123233", "super_admin")).getPrivateKey();
    private static TranCreator tranCreator = TranCreator.newBuilder().setPrivateKey(privateKey).setSignAlgorithm("sha1withecdsa").build();
    private static Peer.ChaincodeId manageNodeCertId = Peer.ChaincodeId.newBuilder().setChaincodeName("ManageNodeCert").setVersion(1).build();

    public static void main(String[] args) throws IOException {
        updateCrlToDB();
//        clearCertStore();
    }

    /**
     * @throws IOException
     */
    public static void updateCrlToDB() throws IOException {
        String tranId = UUID.randomUUID().toString().replace("-", "");
//        String crl = Files.readString(new File("jks/trust_revoke5.crl").toPath());
        String crl = Files.readString(new File("jks/trust-init.crl").toPath());
        Peer.Transaction tran = tranCreator.createInvokeTran(tranId, certId, manageNodeCertId, "updateCrlToDb", JSON.toJSONString(crl));
        // 提交交易
        JSONObject res = new TranPostClient("localhost:8081").postSignedTran(tran);
        System.out.println(res);
    }

    /**
     * @throws IOException
     */
    public static void clearCertStore() {
        String tranId = UUID.randomUUID().toString().replace("-", "");
        Peer.Transaction tran = tranCreator.createInvokeTran(tranId, certId, manageNodeCertId, "clearInitializedCrlList", JSON.toJSONString(true));
        // 提交交易
        JSONObject res = new TranPostClient("localhost:8081").postSignedTran(tran);
        System.out.println(res);
    }
}
