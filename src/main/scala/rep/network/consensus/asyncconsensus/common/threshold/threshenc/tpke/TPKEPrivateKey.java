package rep.network.consensus.asyncconsensus.common.threshold.threshenc.tpke;

import it.unisa.dia.gas.jpbc.Element;
import rep.network.consensus.asyncconsensus.common.threshold.common.PairingElementType;
import java.util.HashMap;


/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-04-17
 * @category	该类实现了TPKE的私钥类。
 */
public class TPKEPrivateKey extends TPKEPublicKey{
    private Element SK = null;
    private int index = -1;

    public TPKEPrivateKey(Tpke tpke,int players, int threshold, Element VK,Element[] VKs,Element SK,int index){
        super(tpke,players, threshold, VK, VKs);
        this.SK = SK;
        this.index = index;
    }

    public TPKEPrivateKey(int players, int threshold, Element VK,Element[] VKs,Element SK,int index){
        this(new Tpke(),players, threshold, VK, VKs,SK,index);
    }

    public TPKEPrivateKey(HashMap<String,Object> hm){
        super(hm);
        this.setState(hm);
    }

    public TPKEPrivateKey(String fileName){
        this(loadFromFile(fileName));
    }

    public void saveKey(String fn){
        this.saveToFile(fn,getState());
    }

    public int getIndex(){return this.index;}

    public HashMap<String,Object> getState(){
        //序列化数据放到map中输出
        HashMap<String,Object> hm = super.getState();
        hm.put("SK",this.tpke.getSerializer().SerializeForData(this.SK, PairingElementType.ZR,true));
        hm.put("index",index);
        return hm;
    }

    private void setState(HashMap<String,Object> hm){
        //根据输入的map，设置当前数据
        if(hm == null) return;
        if(hm.get("SK") != null && hm.get("SK") instanceof String){
            this.SK = this.tpke.getSerializer().Deserialize("0:"+hm.get("SK").toString(),true);
        }

        if(hm.get("index") != null && hm.get("index") instanceof Integer){
            this.index = ((Integer) hm.get("index")).intValue();
        }
    }

    public Element DecryptShare(Element U,byte[] V,Element W){
        Element re = null;
        if(!this.VerifyCipherText(U,V,W)) return re;
        re = U.powZn(this.SK).getImmutable();
        return re;
    }
}
