package rep.network.consensus.asyncconsensus.common.crypto;

import it.unisa.dia.gas.jpbc.Element;
import kotlin.Pair;
import org.javatuples.Triplet;
import rep.network.consensus.asyncconsensus.common.threshold.common.PairingKeyCompute;
import rep.network.consensus.asyncconsensus.common.threshold.threshenc.tpke.TPKEPrivateKey;
import rep.network.consensus.asyncconsensus.common.threshold.threshenc.tpke.TPKEPublicKey;
import rep.network.consensus.asyncconsensus.common.threshold.threshenc.tpke.Tpke;
import rep.network.consensus.asyncconsensus.common.threshold.threshsig.boldyreva.BLSPrivateKey;
import rep.network.consensus.asyncconsensus.common.threshold.threshsig.boldyreva.BLSPublicKey;
import rep.network.consensus.asyncconsensus.common.threshold.threshsig.boldyreva.Boldyreva;
import java.io.File;

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-04-17
 * @category	该类实现密钥的产生和输出。
 */

public class ThresholdKeyFactory {
    public static int TPKE = 1;
    public static int BLS = 2;

    private static String[] nodelist = new String[5];

    public static void initNodeName(){
        nodelist[0] = "121000005l35120456.node1";
        nodelist[1] = "12110107bi45jh675g.node2";
        nodelist[2] = "122000002n00123567.node3";
        nodelist[3] = "921000005k36123789.node4";
        nodelist[4] = "921000006e0012v696.node5";
    }


    public static Pair<TPKEPublicKey, TPKEPrivateKey[]> getTPKEKey(int players,int threshold){
        Tpke tpke = new Tpke();
        Triplet<Element[],Element,Element[]> keyData = PairingKeyCompute.computKey(tpke,players,threshold);
        return PairingKeyCompute.getPublicAndPrivateKey4TPKE(tpke,keyData,players,threshold);
    }

    public static Pair<BLSPublicKey, BLSPrivateKey[]> getBLSKey(int players,int threshold){
        Boldyreva bold = new Boldyreva();
        Triplet<Element[],Element,Element[]> keyData = PairingKeyCompute.computKey(bold,players,threshold);
        return PairingKeyCompute.getPublicAndPrivateKey4BSL(bold,keyData,players,threshold);
    }

    public static void generateKeyToFile(int KeyType,int players,int threshold,String fileNamePrefix,String savePath){
        String path = savePath;
        if(savePath.lastIndexOf(File.separator) < (savePath.length()-1)){
            path = savePath + File.separator;
        }
        switch (KeyType){
            case 1:
                Pair<TPKEPublicKey, TPKEPrivateKey[]> tKeys = getTPKEKey(players,threshold);
                tKeys.component1().saveKey(path+"Threshold-TPKE-PublicKey-"+fileNamePrefix+".key");
                TPKEPrivateKey[] ks = tKeys.component2();
                for(int i = 0; i < ks.length; i++){
                    TPKEPrivateKey k = ks[i];
                    k.saveKey(path+"Threshold-TPKE-PrivateKey-"+fileNamePrefix+"-"+nodelist[i]+".key");
                }
                break;
            case 2:
                Pair<BLSPublicKey, BLSPrivateKey[]> BKeys =  getBLSKey(players,threshold);
                BKeys.component1().saveKey(path+"Threshold-BLS-PublicKey-"+fileNamePrefix+".key");
                BLSPrivateKey[] bs = BKeys.component2();
                for(int i = 0; i < bs.length; i++){
                    BLSPrivateKey k = bs[i];
                    k.saveKey(path+"Threshold-BLS-PrivateKey-"+fileNamePrefix+"-"+nodelist[i]+".key");
                }
                break;
            default:
                System.out.println("Unknow Key Type!");
                break;
        }
    }

    public static void generateKeyToFile(int KeyType,int players,int threshold,String fileNamePrefix){
        String mainPath = System.getProperty("user.dir") + File.separator + "jks"+File.separator+"threshold"+File.separator;
        generateKeyToFile(KeyType,players,threshold,fileNamePrefix,mainPath);
    }

    public static void saveTpkeKey4Batch(Pair<TPKEPublicKey, TPKEPrivateKey[]> tKeys,String fileNamePrefix,String savePath ){
        String path = savePath;
        if(savePath.lastIndexOf(File.separator) < (savePath.length()-1)){
            path = savePath + File.separator;
        }
        tKeys.component1().saveKey(path+"Threshold-TPKE-PublicKey-"+fileNamePrefix+".key");
        TPKEPrivateKey[] ks = tKeys.component2();
        for(int i = 0; i < ks.length; i++){
            TPKEPrivateKey k = ks[i];
            k.saveKey(path+"Threshold-TPKE-PrivateKey-"+fileNamePrefix+"-"+i+".key");
        }
    }

    public static void saveBLSKey4Batch(Pair<BLSPublicKey, BLSPrivateKey[]> tKeys,String fileNamePrefix,String savePath ){
        String path = savePath;
        if(savePath.lastIndexOf(File.separator) < (savePath.length()-1)){
            path = savePath + File.separator;
        }
        tKeys.component1().saveKey(path+"Threshold-BLS-PublicKey-"+fileNamePrefix+".key");
        BLSPrivateKey[] ks = tKeys.component2();
        for(int i = 0; i < ks.length; i++){
            BLSPrivateKey k = ks[i];
            k.saveKey(path+"Threshold-BLS-PrivateKey-"+fileNamePrefix+"-"+i+".key");
        }
    }

    public static void main(String[] args){
        initNodeName();
        generateKeyToFile(TPKE,5,3,"2-3");
        generateKeyToFile(BLS,5,3,"2-3");
        generateKeyToFile(BLS,5,3,"1-3");
    }
}
