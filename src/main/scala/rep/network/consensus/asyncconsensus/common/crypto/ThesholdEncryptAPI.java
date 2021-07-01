package rep.network.consensus.asyncconsensus.common.crypto;

import it.unisa.dia.gas.jpbc.Element;
import org.apache.kerby.util.Hex;
import org.javatuples.Triplet;
import rep.network.consensus.asyncconsensus.common.threshold.common.KeyStorager;
import rep.network.consensus.asyncconsensus.common.threshold.common.PairingElementType;
import rep.network.consensus.asyncconsensus.common.threshold.threshenc.tpke.TPKEPrivateKey;
import rep.network.consensus.asyncconsensus.common.threshold.threshenc.tpke.TPKEPublicKey;
import java.util.Base64;
import java.util.HashMap;

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-04-17
 * @category	提供AES与密钥阈值加密结合起来使用的接口类。
 */

public class ThesholdEncryptAPI {
    private TPKEPublicKey publicKey = null;
    private TPKEPrivateKey privateKey = null;

    private byte[]  cipherContent = null;
    private Triplet<Element,byte[],Element> cipherKey = null;
    private HashMap<Integer,Element> shareDecrypt = new HashMap<Integer,Element>();

    public ThesholdEncryptAPI(TPKEPublicKey publicKey, TPKEPrivateKey privateKey){
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    public String encrypt(byte[] data){
        byte[] key = AesCrypto.generatePasswordIn32Bit();
        System.out.println("password        :"+Hex.encode(key));
        Triplet<Element,byte[],Element> cipherOfKey = this.publicKey.Encrypt(key);
        String cipherContent = Hex.encode(AesCrypto.encryptInAES256(key,data));
        HashMap<String,Object> hm = convertThresholdCipherToMap(cipherOfKey);
        hm.put("ciperContent",cipherContent);
        return KeyStorager.convertHashMapToJSonStr(hm);
    }

    public boolean recvCipherInfo(String cipherInfo){
        boolean rb = true;
        HashMap<String,Object> hm = KeyStorager.convertJSonStrToHashMap(cipherInfo);
        this.cipherKey = this.convertMapToThresholdCipher(hm);
        if(hm.containsKey("ciperContent") && hm.get("ciperContent") != null){
            this.cipherContent = Hex.decode(hm.get("ciperContent").toString());
        }
        if(this.cipherContent == null || this.cipherKey == null) rb = false;
        System.out.println("recvCipherInfo verify cipher content,result="+this.publicKey.VerifyCipherText(this.cipherKey.getValue0(),this.cipherKey.getValue1(),this.cipherKey.getValue2()));
        return rb;
    }

    public String decryptShare(){
        if(this.cipherKey == null) return "";
        HashMap<String,Object> hm = new HashMap<String,Object>();
        Element share = this.privateKey.DecryptShare(this.cipherKey.getValue0(),this.cipherKey.getValue1(),this.cipherKey.getValue2());
        hm.put("share",this.publicKey.getTpke().getSerializer().SerializeForData(share, PairingElementType.G1,true));
        hm.put("index",this.privateKey.getIndex());
        this.shareDecrypt.put(this.privateKey.getIndex(),share);
        System.out.println("decryptShare,index="+this.privateKey.getIndex()+",verify share decrypt="+this.publicKey.VerifyShare(this.privateKey.getIndex(),share,this.cipherKey.getValue0(),this.cipherKey.getValue1(),this.cipherKey.getValue2()));
        return KeyStorager.convertHashMapToJSonStr(hm);
    }

    public boolean recvDecryptShare(String shareinfo){
        boolean rb = false;
        HashMap<String,Object> hm = KeyStorager.convertJSonStrToHashMap(shareinfo);
        Element share = null;
        int index = -1;
        if(hm.get("share") != null && hm.get("share") instanceof String){
            share = this.publicKey.getTpke().getSerializer().Deserialize("1:"+hm.get("share").toString(),true);
        }
        if(hm.get("index") != null && hm.get("index") instanceof Integer){
            index = ((Integer) hm.get("index")).intValue();
        }
        if(index >= 0 && share != null){
            rb = true;
            this.shareDecrypt.put(index,share);
            System.out.println("recv share decrypt,index="+index+",verify share decrypt="+this.publicKey.VerifyShare(index,share,this.cipherKey.getValue0(),this.cipherKey.getValue1(),this.cipherKey.getValue2()));
        }
        return rb;
    }

    public boolean hasDecrypt(){
        if(this.cipherKey != null && this.cipherContent != null && this.shareDecrypt != null && this.shareDecrypt.size()>=this.publicKey.getThreshold()) {
            return true;
        }else{
            return false;
        }
    }

    public byte[] decrypt(){
        if(this.cipherKey != null && this.cipherContent != null && this.shareDecrypt != null && this.shareDecrypt.size()>=this.publicKey.getThreshold()) {
            byte[] key = this.publicKey.CombineShares(this.cipherKey.getValue0(),this.cipherKey.getValue1(),this.cipherKey.getValue2(),this.shareDecrypt);
            System.out.println("decrypt password:"+Hex.encode(key));
            return AesCrypto.decryptInAES256(key,this.cipherContent);
        }else {
            return null;
        }
    }

    private HashMap<String,Object> convertThresholdCipherToMap(Triplet<Element,byte[],Element> cipherOfKey){
        HashMap<String,Object> hm = new HashMap<String,Object>();
        hm.put("U",this.publicKey.getTpke().getSerializer().SerializeForData(cipherOfKey.getValue0(), PairingElementType.G1,true));
        hm.put("V", Base64.getEncoder().encodeToString(cipherOfKey.getValue1()));
        hm.put("W",this.publicKey.getTpke().getSerializer().SerializeForData(cipherOfKey.getValue2(), PairingElementType.G2,true));
        return hm;
    }

    private Triplet<Element,byte[],Element> convertMapToThresholdCipher(HashMap<String,Object> hm){
        Triplet<Element,byte[],Element> cipher = null;
        Element U = null;
        byte[] V = null;
        Element W = null;
        if(hm.get("U") != null && hm.get("U") instanceof String){
            U = this.publicKey.getTpke().getSerializer().Deserialize("1:"+hm.get("U").toString(),true);
        }
        if(hm.get("V") != null && hm.get("V") instanceof String){
            V = Base64.getDecoder().decode(hm.get("V").toString());
        }
        if(hm.get("W") != null && hm.get("W") instanceof String){
            W = this.publicKey.getTpke().getSerializer().Deserialize("2:"+hm.get("W").toString(),true);
        }
        cipher = new Triplet<Element,byte[],Element>(U,V,W);
        return cipher;
    }
}
