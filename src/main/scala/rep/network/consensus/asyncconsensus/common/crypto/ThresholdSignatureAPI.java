package rep.network.consensus.asyncconsensus.common.crypto;

import it.unisa.dia.gas.jpbc.Element;
import org.javatuples.Pair;
import rep.network.consensus.asyncconsensus.common.threshold.common.KeyStorager;
import rep.network.consensus.asyncconsensus.common.threshold.common.PairingElementType;
import rep.network.consensus.asyncconsensus.common.threshold.threshsig.boldyreva.BLSPrivateKey;
import rep.network.consensus.asyncconsensus.common.threshold.threshsig.boldyreva.BLSPublicKey;
import java.util.HashMap;

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-04-17
 * @category	该类主要实现阈值签名的接口类。
 */

public class ThresholdSignatureAPI {
    private BLSPublicKey publicKey = null;
    private BLSPrivateKey privateKey = null;

    private HashMap<Integer,Element> shareSign = new HashMap<Integer, Element>();


    public ThresholdSignatureAPI(BLSPublicKey publicKey, BLSPrivateKey privateKey){
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    public String shareSign(byte[] data){
        Element hash = this.publicKey.getMessageHash(data);
        return shareSignInner(hash);
    }

    public String shareSign(String data){
        Element hash = this.publicKey.getMessageHash(data);
        return shareSignInner(hash);
    }

    public boolean recvShareSign(String SigInfo,String data){
        return this.recvShareSignInner(SigInfo,this.publicKey.getMessageHash(data));
    }

    public boolean recvShareSign(String SigInfo,byte[] data){
        return this.recvShareSignInner(SigInfo,this.publicKey.getMessageHash(data));
    }

    public boolean hasCombine(){
        boolean rb = false;
        if(this.shareSign.size() >= this.publicKey.getThreshold()){
            rb = true;
        }
        return rb;
    }

    public String combineSign(){
        String sig = "";
        Element cs = null;
        if(this.shareSign.size() >= this.publicKey.getThreshold()){
            cs = this.publicKey.CombineShare(this.shareSign);
        }
        if(cs != null){
            sig = this.publicKey.getBold().getSerializer().SerializeForData(cs, PairingElementType.G1,true);
        }
        return sig;
    }

    public boolean verifyCombineSign(String signs,String data){
        return verifyCombineSignInner(signs,this.publicKey.getMessageHash(data));
    }

    public boolean verifyCombineSign(String signs,byte[] data){
        return  verifyCombineSignInner(signs,this.publicKey.getMessageHash(data));
    }

    private boolean verifyCombineSignInner(String signs,Element hash){
        boolean rb = false;
        Element cs = this.publicKey.getBold().getSerializer().Deserialize("1:"+signs,true);
        if(this.publicKey.VerifySignature(cs,hash)){
            rb = true;
            System.out.println("verify combine signature,result=true");
        }else{
            System.out.println("verify combine signature,result=false");
        }
        return rb;
    }

    private boolean recvShareSignInner(String SigInfo,Element hash){
        boolean rb = false;
        HashMap<String,Object> hm = KeyStorager.convertJSonStrToHashMap(SigInfo);
        Pair<Element,Integer> sigPair = this.convertMapToSign(hm);
        if(sigPair == null){
            System.out.println("recv signature error!");
        }else{
            if(this.publicKey.VerifyShare(sigPair.getValue0(),sigPair.getValue1(),hash)){
                this.shareSign.put(sigPair.getValue1(),sigPair.getValue0());
                rb = true;
                System.out.println("recv share signature,index="+sigPair.getValue1()+",result=true");
            }else{
                System.out.println("recv share signature,index="+sigPair.getValue1()+",result=false");
            }
        }
        return rb;
    }

    private String shareSignInner(Element hash){
        Element sig = this.privateKey.Sign(hash);
        HashMap<String,Object> hm = this.convertSignToMap(sig);
        this.shareSign.put(this.privateKey.getIndex(),sig);
        return KeyStorager.convertHashMapToJSonStr(hm);
    }

    private HashMap<String,Object> convertSignToMap(Element sig){
        HashMap<String,Object> hm = new HashMap<String,Object>();
        hm.put("signature",this.publicKey.getBold().getSerializer().SerializeForData(sig, PairingElementType.G1,true));
        hm.put("index", this.privateKey.getIndex());
        return hm;
    }

    private Pair<Element,Integer> convertMapToSign(HashMap<String,Object> hm){
        Pair<Element,Integer> sig = null;
        Element signature = null;
        int index = -1;

        if(hm.get("signature") != null && hm.get("signature") instanceof String){
            signature = this.publicKey.getBold().getSerializer().Deserialize("1:"+hm.get("signature").toString(),true);
        }
        if(hm.get("index") != null && hm.get("index") instanceof Integer){
            index = ((Integer) hm.get("index")).intValue();
        }
        if(index > -1 && signature != null){
            sig = new Pair<Element, Integer>(signature,index);
        }

        return sig;
    }

}
