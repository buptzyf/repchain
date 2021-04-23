package rep.ssl;

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
import java.util.HashMap;
import java.util.UUID;

public class ManageNode {

    private static Peer.CertId certId = Peer.CertId.newBuilder().setCreditCode("951002007l78123233").setCertName("super_admin").build();
    private static PrivateKey privateKey = CertUtil.genX509CertPrivateKey(new File(String.format("jks/951002007l78123233.super_admin.jks")), "super_admin",
            String.format("%s.%s", "951002007l78123233", "super_admin")).getPrivateKey();
    private static TranCreator tranCreator = TranCreator.newBuilder().setPrivateKey(privateKey).setSignAlgorithm("sha1withecdsa").build();
    private static Peer.ChaincodeId manageNodeCertId = Peer.ChaincodeId.newBuilder().setChaincodeName("ManageNodeCert").setVersion(1).build();

    static String node1Name = "121000005l35120456.node1";
    static String node2Name = "12110107bi45jh675g.node2";
    static String node3Name = "122000002n00123567.node3";
    static String node4Name = "921000005k36123789.node4";
    static String node5Name = "921000006e0012v696.node5";

    public static void main(String[] args) throws IOException {
        // init
//        String[] nodeNames = new String[]{node1Name, node2Name, node3Name, node4Name};
//        writeCertMapToDB("initNodeCert", nodeNames);
        // update
        String[] nodeNames = new String[]{node3Name};
        writeCertMapToDB("updateNodeCert", nodeNames);
    }

    /**
     * @param function
     * @param nodeNames
     * @throws IOException
     */
    public static void writeCertMapToDB(String function, String[] nodeNames) throws IOException {

        String tranId = UUID.randomUUID().toString().replace("-", "");
        HashMap<String, String> certMap = new HashMap<>();

        if (function.equals("initNodeCert")) {
            // 模拟初始化证书
            for (String nodeName : nodeNames) {
                String nodeCert = Files.readString(new File(String.format("jks/%s.cer", nodeName)).toPath());
                certMap.put(nodeName, nodeCert);
            }
        } else if (function.equals("updateNodeCert")) {
            // 模拟删除证书
            for (String nodeName : nodeNames) {
                certMap.put(nodeName, "");
            }
        }

        Peer.Transaction tran = tranCreator.createInvokeTran(tranId, certId, manageNodeCertId, function, JSON.toJSONString(certMap));

        JSONObject res = new TranPostClient("localhost:8081").postSignedTran(tran);
        System.out.println(res);

    }

}
