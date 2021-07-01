package rep.network.consensus.asyncconsensus.common.threshold.threshsig.boldyreva;

import it.unisa.dia.gas.jpbc.Element;
import rep.network.consensus.asyncconsensus.common.threshold.common.KeyStorager;
import rep.network.consensus.asyncconsensus.common.threshold.common.PairingElementType;
import rep.network.consensus.asyncconsensus.common.threshold.common.PairingLagrange;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-04-17
 * @category	该类实现了BLS的公钥。
 */
public class BLSPublicKey extends KeyStorager {
    protected Boldyreva bold = null;
    protected int players = 0;
    protected int threshold = 0;
    protected Element VK = null;
    protected Element[] VKs = null;

    public BLSPublicKey(Boldyreva bold, int players, int threshold, Element VK, Element[] VKs){
        this.bold = bold;
        this.players = players;
        this.threshold = threshold;
        this.VK = VK;
        this.VKs = VKs;
    }

    public BLSPublicKey(int players, int threshold, Element VK,Element[] VKs){
        this(new Boldyreva(),players, threshold, VK, VKs);
    }

    public BLSPublicKey(HashMap<String,Object> hm){
        this.bold = new Boldyreva();
        this.setState(hm);
    }

    public BLSPublicKey(String fileName){
        this.bold = new Boldyreva();
        HashMap<String,Object> hm = this.loadFromFile(fileName);
        this.setState(hm);
    }

    public void saveKey(String fn){
        this.saveToFile(fn,getState());
    }

    public Boldyreva getBold(){return this.bold;}

    public int getThreshold(){return this.threshold;}

    public int getPlayers(){return this.players;}

    public HashMap<String,Object> getState(){
        //序列化数据放到map中输出
        HashMap<String,Object> hm = new HashMap<String,Object>();
        hm.put("VK",this.bold.getSerializer().SerializeForData(this.VK, PairingElementType.G2,true));
        String[] els = new String[this.VKs.length];
        for(int i = 0; i < this.VKs.length; i++){
            els[i] = this.bold.getSerializer().SerializeForData(this.VKs[i],PairingElementType.G2,true);
        }
        hm.put("VKS",els);
        hm.put("player",this.players);
        hm.put("threshold",this.threshold);
        return hm;
    }

    public void setState(HashMap<String,Object> hm){
        //根据输入的map，设置当前数据
        if(hm == null) return;
        if(hm.get("VK") != null && hm.get("VK") instanceof String){
            this.VK = this.bold.getSerializer().Deserialize("2:"+hm.get("VK").toString(),true);
        }
        if(hm.get("VKS") != null && hm.get("VKS") instanceof ArrayList){
            ArrayList data = (ArrayList) hm.get("VKS");
            this.VKs = new Element[data.size()];
            for(int i = 0; i < data.size(); i++){
                this.VKs[i] = this.bold.getSerializer().Deserialize("2:"+data.get(i),true);
            }
        }

        if(hm.get("player") != null && hm.get("player") instanceof Integer){
            this.players = ((Integer) hm.get("player")).intValue();
        }

        if(hm.get("threshold") != null && hm.get("threshold") instanceof Integer){
            this.threshold = ((Integer) hm.get("threshold")).intValue();
        }
    }

    public Element getMessageHash( String str)  {
        byte[] b = null;
        try {
            b = str.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        if(b == null) return null;
        return this.getMessageHash(b);
    }

    public Element getMessageHash(byte[] b){
        Element we = this.bold.getGroup().getG1().newElement().setFromHash(b,0,b.length).getImmutable();
        return we;
    }

    public boolean VerifyShare(Element sig,int i, Element h){
        boolean rb = false;
        Element B = this.VKs[i].duplicate();
        Element a1 = this.bold.getGroup().pairing(sig,this.bold.getG2()).getImmutable();
        Element a2 = this.bold.getGroup().pairing(h,B).getImmutable();
        if(a1.isEqual(a2)){
            rb = true;
        }
        return rb;
    }

    public boolean VerifySignature(Element sig,Element h){
        boolean rb = false;
        if(this.bold.getGroup().pairing(sig,this.bold.getG2()).isEqual(this.bold.getGroup().pairing(h,this.VK))){
            rb = true;
        }
        return rb;
    }

    public Element CombineShare(HashMap<Integer,Element> sigs){
        Integer[] S = new Integer[sigs.size()];
        sigs.keySet().toArray(S);
        Arrays.sort(S);

        Element[] els = PairingLagrange.computeLagrangePow(this.bold.getONE(),sigs,S);
        Element result = null;
        for(int i = 0; i < els.length; i++){
            if(result == null){
                result = els[i].getImmutable();
            }else{
                result = result.mul(els[i]).getImmutable();
            }
        }
        return result;
    }


}
