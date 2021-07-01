package rep.network.consensus.asyncconsensus.common.threshold.threshsig.experiment;

import it.unisa.dia.gas.jpbc.Element;
import kotlin.Pair;
import rep.network.consensus.asyncconsensus.common.crypto.ThresholdKeyFactory;
import rep.network.consensus.asyncconsensus.common.threshold.threshsig.boldyreva.BLSPrivateKey;
import rep.network.consensus.asyncconsensus.common.threshold.threshsig.boldyreva.BLSPublicKey;
import rep.network.consensus.asyncconsensus.common.threshold.threshsig.boldyreva.Boldyreva;
import java.io.File;
import java.util.HashMap;
import java.util.Random;


/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-04-17
 * @category	该类测试BLS的阈值签名（内存/文件）。
 */
public class boldyrevaExample {

    public static void test_bodyreva(){
        Boldyreva bold = new Boldyreva();
        //int players = 16;
        //int threshold = 11;

        int players = 1024;
        int threshold = 683;

        Pair<BLSPublicKey, BLSPrivateKey[]> kpair = ThresholdKeyFactory.getBLSKey(players,threshold);

        Element[] sigs = new Element[players];
        Element hash = kpair.component1().getMessageHash("hi");

        for(int i = 0; i < players; i++){
            BLSPrivateKey pk = kpair.component2()[i];
            long start = System.currentTimeMillis();
            sigs[i] = pk.Sign(hash);
            long start1 = System.currentTimeMillis();
            boolean vr = kpair.component1().VerifyShare(sigs[i],i,hash);
            long end = System.currentTimeMillis();
            System.out.println("verify share i="+i+",result="+vr+",share signature time:"+(start1-start)+", verify share signature time:"+(end - start1));

        }

        HashMap<Integer,Element> rshares = new HashMap<Integer,Element>();
        Random r = new Random();

        while(rshares.size()<threshold){
            int rd = r.nextInt(players);
            if(rshares.containsKey(rd)) continue;
            rshares.put(rd,sigs[rd]);
        }
        //rshares.put(0,sigs[0]);
        //rshares.put(1,sigs[1]);
        //rshares.put(2,sigs[2]);
        //rshares.put(3,sigs[3]);
        //rshares.put(4,sigs[4]);

        long start = System.currentTimeMillis();
        Element sig = kpair.component1().CombineShare(rshares);
        long start1 = System.currentTimeMillis();
        if(kpair.component1().VerifySignature(sig,hash)){
            //System.out.println("combine signature=yes");
        }else{
            //System.out.println("combine signature no");
        }
        long end = System.currentTimeMillis();
        System.out.println("combine signature time:="+(start1-start)+" , Verify combine signature time="+(end - start1));
    }

    private static String[] nodelist = new String[5];

    public static void initNodeName(){
        nodelist[0] = "121000005l35120456.node1";
        nodelist[1] = "12110107bi45jh675g.node2";
        nodelist[2] = "122000002n00123567.node3";
        nodelist[3] = "921000005k36123789.node4";
        nodelist[4] = "921000006e0012v696.node5";
    }

    public static Pair<BLSPublicKey, BLSPrivateKey[]> loadBLSKey4Batch() {
        initNodeName();
        String path = System.getProperty("user.dir") + File.separator + "jks"+File.separator+"threshold"+File.separator;
        BLSPublicKey publickey = new BLSPublicKey(path+"Threshold-BLS-PublicKey-"+"2-3"+".key");
        BLSPrivateKey[] pkeys = new BLSPrivateKey[5];
        pkeys[0] = new BLSPrivateKey(path+"Threshold-BLS-PrivateKey-"+"2-3"+"-"+nodelist[0]+".key");
        pkeys[1] = new BLSPrivateKey(path+"Threshold-BLS-PrivateKey-"+"2-3"+"-"+nodelist[1]+".key");
        pkeys[2] = new BLSPrivateKey(path+"Threshold-BLS-PrivateKey-"+"2-3"+"-"+nodelist[2]+".key");
        pkeys[3] = new BLSPrivateKey(path+"Threshold-BLS-PrivateKey-"+"2-3"+"-"+nodelist[3]+".key");
        pkeys[4] = new BLSPrivateKey(path+"Threshold-BLS-PrivateKey-"+"2-3"+"-"+nodelist[4]+".key");
        return new Pair<BLSPublicKey, BLSPrivateKey[]>(publickey,pkeys);
    }


    public static void test_bodyreva_file_key(){
        Boldyreva bold = new Boldyreva();
        int players = 5;
        int threshold = 3;
        Pair<BLSPublicKey, BLSPrivateKey[]> kpair = ThresholdKeyFactory.getBLSKey(players,threshold);

        Element[] sigs = new Element[players];
        Element hash = kpair.component1().getMessageHash("hi");



        for(int i = 0; i < players; i++){
            BLSPrivateKey pk = kpair.component2()[i];
            long start = System.currentTimeMillis();
            sigs[i] = pk.Sign(hash);
            long end = System.currentTimeMillis();
            System.out.println("sig i="+i+",share signature  time:"+(end - start));
        }

        ThresholdKeyFactory.saveBLSKey4Batch(kpair,"test",System.getProperty("user.dir") + File.separator + "jks"+File.separator+"threshold"+File.separator );
        kpair = loadBLSKey4Batch();

        for(int i = 0; i < sigs.length; i++){
            long start = System.currentTimeMillis();
            boolean vr = kpair.component1().VerifyShare(sigs[i],i,hash);
            long end = System.currentTimeMillis();
            System.out.println("verify share i="+i+",result="+vr+", verify share signature time:"+(end - start));
        }


        HashMap<Integer,Element> rshares = new HashMap<Integer,Element>();
        Random r = new Random();

        while(rshares.size()<threshold){
            int rd = r.nextInt(players);
            if(rshares.containsKey(rd)) continue;
            rshares.put(rd,sigs[rd]);
        }
        //rshares.put(0,sigs[0]);
        //rshares.put(1,sigs[1]);
        //rshares.put(2,sigs[2]);
        //rshares.put(3,sigs[3]);
        //rshares.put(4,sigs[4]);

        long start = System.currentTimeMillis();
        Element sig = kpair.component1().CombineShare(rshares);
        if(kpair.component1().VerifySignature(sig,hash)){
            System.out.println("combine signature=yes");
        }else{
            System.out.println("combine signature no");
        }
        long end = System.currentTimeMillis();
        System.out.println("combine signature and Verify combine signature time="+(end - start));
    }

    public static void main(String[] args){
        test_bodyreva();
        //test_bodyreva_file_key();
    }
}
