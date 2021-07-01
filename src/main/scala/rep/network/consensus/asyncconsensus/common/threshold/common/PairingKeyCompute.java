package rep.network.consensus.asyncconsensus.common.threshold.common;

import it.unisa.dia.gas.jpbc.Element;
import kotlin.Pair;
import org.javatuples.Triplet;
import rep.network.consensus.asyncconsensus.common.threshold.threshenc.tpke.TPKEPrivateKey;
import rep.network.consensus.asyncconsensus.common.threshold.threshenc.tpke.TPKEPublicKey;
import rep.network.consensus.asyncconsensus.common.threshold.threshenc.tpke.Tpke;
import rep.network.consensus.asyncconsensus.common.threshold.threshsig.boldyreva.BLSPrivateKey;
import rep.network.consensus.asyncconsensus.common.threshold.threshsig.boldyreva.BLSPublicKey;
import rep.network.consensus.asyncconsensus.common.threshold.threshsig.boldyreva.Boldyreva;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-04-17
 * @category	该类实现密钥的生成计算。
 */

public class PairingKeyCompute {
    public static Element[] computeSK(int players,Element[] polynomialCoefficients,Element ZERO,Element ONE){
        Element[] SKs =  new Element[players];
        for(int i = 1; i < players+1; i++){
            SKs[i-1] = PairingPolynomial.PolynomialEvaluation(polynomialCoefficients,i,ZERO,ONE);
        }

        if(PairingPolynomial.PolynomialEvaluation(polynomialCoefficients,0,ZERO,ONE).isEqual(polynomialCoefficients[0].duplicate())){
            System.out.println("PolynomialEvaluation(0) == polynomialCoefficients[0]");
        }
        return SKs;
    }

    public static Element computCommonVK(Element g2,Element polynomialCoefficients0){
        return g2.powZn(polynomialCoefficients0).getImmutable();
    }

    public static Element[] computCommonVKs(Element g2,Element[] SKs){
        Element[] VKs = new Element[SKs.length];
        for(int i = 0; i < VKs.length; i++){
            VKs[i] = g2.powZn(SKs[i]).getImmutable();
        }
        return VKs;
    }

    public static boolean checkReconstructionOf0(Element ZERO,Element ONE,Element[] polynomialCoefficients,int threshold){
        boolean rb = false;
        List<Integer> S = new ArrayList<Integer>();
        for(int i = 0; i < threshold; i++){
            S.add(i);
        }
        Element lhs = PairingPolynomial.PolynomialEvaluation(polynomialCoefficients,0,ZERO,ONE);
        Element rhs = null;

        for(int i = 0; i < threshold; i++){
            int j = S.get(i);
            Element f = PairingPolynomial.PolynomialEvaluation(polynomialCoefficients,j+1,ZERO,ONE);
            Element tmplag = PairingLagrange.Lagrange(ONE,S,j);
            Element tmpresult = tmplag.mulZn(f);
            if(rhs == null){
                rhs = tmpresult.duplicate();
            }else{
                rhs = rhs.add(tmpresult).getImmutable();
            }
        }

        if(lhs.isEqual(rhs)){
            rb = true;
            System.out.println("lhs=rhs");
        }
        return rb;
    }

    public static Pair<TPKEPublicKey, TPKEPrivateKey[]> getPublicAndPrivateKey4TPKE(IPairing pairing, int players, int threshold, Element VK, Element[]VKs, Element[] SKs){
        if(pairing instanceof Tpke){
            Tpke tpke = (Tpke)pairing;
            TPKEPublicKey public_key = new TPKEPublicKey(tpke,players, threshold,VK,VKs);
            TPKEPrivateKey[] private_keys = new TPKEPrivateKey[SKs.length];
            for(int i = 0; i < private_keys.length; i++){
                private_keys[i] = new TPKEPrivateKey(tpke,players,threshold,VK,VKs,SKs[i],i);
            }
            return new Pair<TPKEPublicKey,TPKEPrivateKey[]>(public_key,private_keys);
        }else{
            return null;
        }
    }

    public static Pair<TPKEPublicKey, TPKEPrivateKey[]> getPublicAndPrivateKey4TPKE(IPairing pairing,Triplet<Element[],Element,Element[]> keyData,int players,int threshold){
        return getPublicAndPrivateKey4TPKE(pairing,players,threshold,keyData.getValue1(),keyData.getValue2(),keyData.getValue0());
    }

    public static Pair<BLSPublicKey, BLSPrivateKey[]> getPublicAndPrivateKey4BSL(IPairing pairing, int players, int threshold, Element VK, Element[]VKs, Element[] SKs){
        if(pairing instanceof Boldyreva){
            Boldyreva bold = (Boldyreva)pairing;
            BLSPublicKey public_key = new BLSPublicKey(bold,players, threshold,VK,VKs);
            BLSPrivateKey[] private_keys = new BLSPrivateKey[SKs.length];
            for(int i = 0; i < private_keys.length; i++){
                private_keys[i] = new BLSPrivateKey(bold,players,threshold,VK,VKs,SKs[i],i);
            }
            return new Pair<BLSPublicKey,BLSPrivateKey[]>(public_key,private_keys);
        }else{
            return null;
        }
    }

    public static Pair<BLSPublicKey, BLSPrivateKey[]> getPublicAndPrivateKey4BSL(IPairing pairing,Triplet<Element[],Element,Element[]> keyData,int players,int threshold){
        return getPublicAndPrivateKey4BSL(pairing,players,threshold,keyData.getValue1(),keyData.getValue2(),keyData.getValue0());
    }

    public static Triplet<Element[],Element,Element[]> computKey(IPairing pairing, int players, int threshold){
        Triplet<Element[],Element,Element[]> rdata = null;
        Element[] a = PairingPolynomial.PolynomialCoefficients(pairing.getRandom(),threshold);
        Element[] SKs = PairingKeyCompute.computeSK(players,a,pairing.getZERO(),pairing.getONE());
        Element VK = PairingKeyCompute.computCommonVK(pairing.getG2(),a[0]);
        Element[] VKs = PairingKeyCompute.computCommonVKs(pairing.getG2(),SKs);
        boolean hasSuccess = PairingKeyCompute.checkReconstructionOf0(pairing.getZERO(),pairing.getONE(),a,threshold);
        if(hasSuccess){
            System.out.println("privateKey and publicKey created success!");
            rdata = new Triplet<Element[],Element,Element[]>(SKs,VK,VKs);
            return rdata;
        }else{
            System.out.println("privateKey and publicKey created failed!");
            return null;
        }
    }


}
