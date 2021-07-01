package rep.network.consensus.asyncconsensus.common.threshold.threshenc.experiment;

import it.unisa.dia.gas.jpbc.Element;
import kotlin.Pair;
import org.apache.kerby.util.Hex;
import org.javatuples.Triplet;
import rep.crypto.Sha256;
import rep.network.consensus.asyncconsensus.common.crypto.ThresholdKeyFactory;
import rep.network.consensus.asyncconsensus.common.threshold.threshenc.tpke.TPKEPrivateKey;
import rep.network.consensus.asyncconsensus.common.threshold.threshenc.tpke.TPKEPublicKey;
import rep.network.consensus.asyncconsensus.common.threshold.threshenc.tpke.Tpke;
import java.io.File;
import java.util.*;

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-04-17
 * @category	该类测试阈值加密计算（内存/文件）。
 */

public class TpkeExample {

    public static void test_tpke(){
        Tpke tpke = new Tpke();
        int players = 100;
        int threshold = 66;
        //Pair<TPKEPublicKey, TPKEPrivateKey[]> kpair = tpke.Dealer(players,threshold);
        Pair<TPKEPublicKey, TPKEPrivateKey[]> kpair = ThresholdKeyFactory.getTPKEKey(players,threshold);
        byte[] m = Sha256.hashToBytes("how");
        String m1 = Hex.encode(m);
        Triplet<Element,byte[],Element> ciphertext = kpair.component1().Encrypt(m);
        if(kpair.component1().VerifyCipherText(ciphertext.getValue0(),ciphertext.getValue1(),ciphertext.getValue2())){
            System.out.println("verify ciphertext :yes");
        }
        Element[] shares = new Element[kpair.component2().length];
        for(int i = 0; i < kpair.component2().length; i++){
            TPKEPrivateKey pk = kpair.component2()[i];
            shares[i] = pk.DecryptShare(ciphertext.getValue0(),ciphertext.getValue1(),ciphertext.getValue2());
        }

        for(int i = 0; i < shares.length; i++){
            if(kpair.component1().VerifyShare(i,shares[i],ciphertext.getValue0(),ciphertext.getValue1(),ciphertext.getValue2())){
                System.out.println("verify share :yes");
            }
        }

        HashMap<Integer,Element> rshares = new HashMap<Integer,Element>();
        Random r = new Random();

        while(rshares.size()<threshold){
            int rd = r.nextInt(players);
            if(rshares.containsKey(rd)) continue;
            rshares.put(rd,shares[rd]);
        }
        //rshares.put(0,shares[0]);
        //rshares.put(1,shares[1]);
        //rshares.put(2,shares[2]);
        //rshares.put(3,shares[3]);
        //rshares.put(4,shares[4]);

        /*for(int i = 0 ; i < threshold; i++){
            int rd = r.nextInt(players);
            rshares.put(rd,shares[rd]);
        }*/

        long start = System.currentTimeMillis();
        byte[] dm = kpair.component1().CombineShares(ciphertext.getValue0(),ciphertext.getValue1(),ciphertext.getValue2(),rshares);
        long end = System.currentTimeMillis();
        System.out.println("share decrypt time:"+(end-start)+"ms");
        String m2 = Hex.encode(dm);
        if(Arrays.equals(m,dm)){
            System.out.println("共享解密结果 :yes");
            System.out.println("src  byte string="+m1);
            System.out.println("dest byte string="+m2);
        }else{
            System.out.println("共享解密结果 :no");
            System.out.println("src  byte string="+m1);
            System.out.println("dest byte string="+m2);
        }
    }

    private static String[] nodelist = new String[5];

    public static void initNodeName(){
        nodelist[0] = "121000005l35120456.node1";
        nodelist[1] = "12110107bi45jh675g.node2";
        nodelist[2] = "122000002n00123567.node3";
        nodelist[3] = "921000005k36123789.node4";
        nodelist[4] = "921000006e0012v696.node5";
    }

    public static Pair<TPKEPublicKey, TPKEPrivateKey[]> loadTpkeKey4Batch() {
        initNodeName();
        String path = System.getProperty("user.dir") + File.separator + "jks"+File.separator+"threshold"+File.separator;
        TPKEPublicKey publickey = new TPKEPublicKey(path+"Threshold-TPKE-PublicKey-"+"2-3"+".key");
        TPKEPrivateKey[] pkeys = new TPKEPrivateKey[5];
        pkeys[0] = new TPKEPrivateKey(path+"Threshold-TPKE-PrivateKey-"+"2-3"+"-"+nodelist[0]+".key");
        pkeys[1] = new TPKEPrivateKey(path+"Threshold-TPKE-PrivateKey-"+"2-3"+"-"+nodelist[1]+".key");
        pkeys[2] = new TPKEPrivateKey(path+"Threshold-TPKE-PrivateKey-"+"2-3"+"-"+nodelist[2]+".key");
        pkeys[3] = new TPKEPrivateKey(path+"Threshold-TPKE-PrivateKey-"+"2-3"+"-"+nodelist[3]+".key");
        pkeys[4] = new TPKEPrivateKey(path+"Threshold-TPKE-PrivateKey-"+"2-3"+"-"+nodelist[4]+".key");
        return new Pair<TPKEPublicKey, TPKEPrivateKey[]>(publickey,pkeys);
    }

    public static void test_tpke_file_key(){
        Tpke tpke = new Tpke();
        int players = 5;
        int threshold = 3;
        //Pair<TPKEPublicKey, TPKEPrivateKey[]> kpair = tpke.Dealer(players,threshold);
        Pair<TPKEPublicKey, TPKEPrivateKey[]> kpair = ThresholdKeyFactory.getTPKEKey(players,threshold);
        byte[] m = Sha256.hashToBytes("how");
        String m1 = Hex.encode(m);
        Triplet<Element,byte[],Element> ciphertext = kpair.component1().Encrypt(m);
        if(kpair.component1().VerifyCipherText(ciphertext.getValue0(),ciphertext.getValue1(),ciphertext.getValue2())){
            System.out.println("verify ciphertext :yes");
        }

        ThresholdKeyFactory.saveTpkeKey4Batch(kpair,"test",System.getProperty("user.dir") + File.separator + "jks"+File.separator+"threshold"+File.separator );
        kpair = loadTpkeKey4Batch();

        Element[] shares = new Element[kpair.component2().length];
        for(int i = 0; i < kpair.component2().length; i++){
            TPKEPrivateKey pk = kpair.component2()[i];
            shares[i] = pk.DecryptShare(ciphertext.getValue0(),ciphertext.getValue1(),ciphertext.getValue2());
        }

        for(int i = 0; i < shares.length; i++){
            if(kpair.component1().VerifyShare(i,shares[i],ciphertext.getValue0(),ciphertext.getValue1(),ciphertext.getValue2())){
                System.out.println("verify share :yes");
            }
        }

        HashMap<Integer,Element> rshares = new HashMap<Integer,Element>();
        Random r = new Random();

        while(rshares.size()<threshold){
            int rd = r.nextInt(players);
            if(rshares.containsKey(rd)) continue;
            rshares.put(rd,shares[rd]);
        }
        //rshares.put(0,shares[0]);
        //rshares.put(1,shares[1]);
        //rshares.put(2,shares[2]);
        //rshares.put(3,shares[3]);
        //rshares.put(4,shares[4]);

        /*for(int i = 0 ; i < threshold; i++){
            int rd = r.nextInt(players);
            rshares.put(rd,shares[rd]);
        }*/

        long start = System.currentTimeMillis();
        byte[] dm = kpair.component1().CombineShares(ciphertext.getValue0(),ciphertext.getValue1(),ciphertext.getValue2(),rshares);
        long end = System.currentTimeMillis();
        System.out.println("share decrypt time:"+(end-start)+"ms");
        String m2 = Hex.encode(dm);
        if(Arrays.equals(m,dm)){
            System.out.println("共享解密结果 :yes");
            System.out.println("src  byte string="+m1);
            System.out.println("dest byte string="+m2);
        }else{
            System.out.println("共享解密结果 :no");
            System.out.println("src  byte string="+m1);
            System.out.println("dest byte string="+m2);
        }
    }


    public static void main(String[] args){
        /*val h = Sha256.hashToBytes("how")
        println(BytesHex.bytes2hex(h))*/
        test_tpke();
        //test_tpke_file_key();
    }

}
