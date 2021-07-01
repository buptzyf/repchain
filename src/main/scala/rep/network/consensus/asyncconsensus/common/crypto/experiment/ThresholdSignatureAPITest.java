package rep.network.consensus.asyncconsensus.common.crypto.experiment;

import kotlin.Pair;
import rep.network.consensus.asyncconsensus.common.crypto.ThresholdSignatureAPI;
import rep.network.consensus.asyncconsensus.common.threshold.threshsig.boldyreva.BLSPrivateKey;
import rep.network.consensus.asyncconsensus.common.threshold.threshsig.boldyreva.BLSPublicKey;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-04-17
 * @category	该类主要测试阈值签名的接口类。
 */

public class ThresholdSignatureAPITest {

    public static Pair<BLSPublicKey, BLSPrivateKey[]> loadBLSKey4Batch() {
        String path = System.getProperty("user.dir") + File.separator + "jks"+File.separator+"threshold"+File.separator;
        BLSPublicKey publickey = new BLSPublicKey(path+"Threshold-BLS-PublicKey-"+"test"+".key");
        BLSPrivateKey[] pkeys = new BLSPrivateKey[5];
        pkeys[0] = new BLSPrivateKey(path+"Threshold-BLS-PrivateKey-"+"test"+"-"+0+".key");
        pkeys[1] = new BLSPrivateKey(path+"Threshold-BLS-PrivateKey-"+"test"+"-"+1+".key");
        pkeys[2] = new BLSPrivateKey(path+"Threshold-BLS-PrivateKey-"+"test"+"-"+2+".key");
        pkeys[3] = new BLSPrivateKey(path+"Threshold-BLS-PrivateKey-"+"test"+"-"+3+".key");
        pkeys[4] = new BLSPrivateKey(path+"Threshold-BLS-PrivateKey-"+"test"+"-"+4+".key");
        return new Pair<BLSPublicKey, BLSPrivateKey[]>(publickey,pkeys);
    }

    public static void test(){
        String  data = "woriulSfjwi脸上肌肤时刻92398023时尚大方！！4";
        Pair<BLSPublicKey, BLSPrivateKey[]> keyPair = loadBLSKey4Batch();
        ThresholdSignatureAPI[] sigApi = new ThresholdSignatureAPI[5];
        for(int i = 0; i < keyPair.component2().length; i++){
            sigApi[i] = new ThresholdSignatureAPI(keyPair.component1(),keyPair.component2()[i]);
        }

        List<String> shares = new ArrayList<String>();

        for(int i = 0; i < sigApi.length; i++){
            shares.add(sigApi[i].shareSign(data));
        }

        List<String> rshares = new ArrayList<>();
        Random r = new Random();

        while(rshares.size()<keyPair.component1().getThreshold()){
            int rd = r.nextInt(5);
            if(rshares.contains(shares.get(rd))) continue;
            rshares.add(shares.get(rd));
        }

        int nodenum = r.nextInt(5) % 5;
        ThresholdSignatureAPI st = sigApi[nodenum];
        for (String value : rshares) {
           st.recvShareSign(value,data);
        }

        if(st.hasCombine()){
            String cstr = st.combineSign();
            System.out.println("combine signatrue :"+cstr);
            st.verifyCombineSign(cstr,data);
        }
    }

    public static void main(String[] args){
        test();
    }
}
