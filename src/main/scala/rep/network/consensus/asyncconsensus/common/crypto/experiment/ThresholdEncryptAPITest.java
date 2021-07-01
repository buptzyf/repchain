package rep.network.consensus.asyncconsensus.common.crypto.experiment;

import kotlin.Pair;
import rep.network.consensus.asyncconsensus.common.crypto.ThesholdEncryptAPI;
import rep.network.consensus.asyncconsensus.common.threshold.threshenc.tpke.TPKEPrivateKey;
import rep.network.consensus.asyncconsensus.common.threshold.threshenc.tpke.TPKEPublicKey;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Random;

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-04-17
 * @category	该类主要测试阈值加密AES密钥与AES对称加密数据的接口类。
 */

public class ThresholdEncryptAPITest {

    public static Pair<TPKEPublicKey, TPKEPrivateKey[]> loadTpkeKey4Batch() {
        String path = System.getProperty("user.dir") + File.separator + "jks"+File.separator+"threshold"+File.separator;
        TPKEPublicKey publickey = new TPKEPublicKey(path+"Threshold-TPKE-PublicKey-"+"test"+".key");
        TPKEPrivateKey[] pkeys = new TPKEPrivateKey[5];
        pkeys[0] = new TPKEPrivateKey(path+"Threshold-TPKE-PrivateKey-"+"test"+"-"+0+".key");
        pkeys[1] = new TPKEPrivateKey(path+"Threshold-TPKE-PrivateKey-"+"test"+"-"+1+".key");
        pkeys[2] = new TPKEPrivateKey(path+"Threshold-TPKE-PrivateKey-"+"test"+"-"+2+".key");
        pkeys[3] = new TPKEPrivateKey(path+"Threshold-TPKE-PrivateKey-"+"test"+"-"+3+".key");
        pkeys[4] = new TPKEPrivateKey(path+"Threshold-TPKE-PrivateKey-"+"test"+"-"+4+".key");
        return new Pair<TPKEPublicKey, TPKEPrivateKey[]>(publickey,pkeys);
    }

    public static void test(){
        byte[] data = null;
        try {
            data = "sfsdfjklsfjllsdjfklwer9u23234你好！！###".getBytes("UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
        }
        Pair<TPKEPublicKey, TPKEPrivateKey[]> keyPair = loadTpkeKey4Batch();
        ThesholdEncryptAPI[] aesAndTpkes = new ThesholdEncryptAPI[5];
        for(int i = 0; i < keyPair.component2().length; i++){
            aesAndTpkes[i] = new ThesholdEncryptAPI(keyPair.component1(),keyPair.component2()[i]);
        }

        String cipherobject = aesAndTpkes[0].encrypt(data);

        for(int i = 0; i < aesAndTpkes.length; i++){
            if(aesAndTpkes[i].recvCipherInfo(cipherobject)){
                System.out.println("node "+i+" recv cipher data success!");
            }else{
                System.out.println("node "+i+" recv cipher data failed!");
            }
        }

        String[] shares = new String[aesAndTpkes.length];
        for(int i = 0; i < aesAndTpkes.length; i++){
            ThesholdEncryptAPI at = aesAndTpkes[i];
            shares[i] =  at.decryptShare();
        }

        HashMap<Integer,String> rshares = new HashMap<Integer,String>();
        Random r = new Random();

        while(rshares.size()<keyPair.component1().getThreshold()){
            int rd = r.nextInt(5);
            if(rshares.containsKey(rd)) continue;
            rshares.put(rd,shares[rd]);
        }

        int nodenum = r.nextInt(5) % 5;
        ThesholdEncryptAPI at = aesAndTpkes[nodenum];
        for (String value : rshares.values()) {
            at.recvDecryptShare(value);
        }

        if(at.hasDecrypt()){
            byte[] dedata = at.decrypt();
            try {
                String str = new String(data,"UTF-8");
                String destr = new String(dedata,"UTF-8");

                System.out.println("source="+str);
                System.out.println("destr ="+destr);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args){
        test();
    }
}
