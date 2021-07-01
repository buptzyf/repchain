package rep.network.consensus.asyncconsensus.common.threshold.threshsig.boldyreva;

import it.unisa.dia.gas.jpbc.Element;
import rep.network.consensus.asyncconsensus.common.threshold.common.PairingElementType;
import java.util.HashMap;


/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-04-17
 * @category	该类实现了BLS的私钥类。
 */
public class BLSPrivateKey extends BLSPublicKey {
    private Element SK = null;
    private int index = -1;

    public BLSPrivateKey(Boldyreva bold, int players, int threshold, Element VK, Element[] VKs, Element SK, int index){
        super(bold,players, threshold, VK, VKs);
        this.SK = SK;
        this.index = index;
    }

    public BLSPrivateKey(int players, int threshold, Element VK,Element[] VKs, Element SK, int index){
        this(new Boldyreva(),players, threshold, VK, VKs,SK,index);
    }

    public BLSPrivateKey(HashMap<String,Object> hm){
        super(hm);
        this.setState(hm);
    }

    public BLSPrivateKey(String fileName){
        this(loadFromFile(fileName));
    }

    public void saveKey(String fn){
        this.saveToFile(fn,getState());
    }

    public int getIndex(){return this.index;}

    public HashMap<String,Object> getState(){
        //序列化数据放到map中输出
        HashMap<String,Object> hm = super.getState();
        hm.put("SK",this.bold.getSerializer().SerializeForData(this.SK, PairingElementType.ZR,true));
        hm.put("index",this.index);
        return hm;
    }

    public void setState(HashMap<String,Object> hm){
        //根据输入的map，设置当前数据
        if(hm == null) return;
        super.setState(hm);
        if(hm.get("SK") != null && hm.get("SK") instanceof String){
            this.SK = this.bold.getSerializer().Deserialize("0:"+hm.get("SK").toString(),true);
        }

        if(hm.get("index") != null && hm.get("index") instanceof Integer){
            this.index = ((Integer) hm.get("index")).intValue();
        }
    }

    public Element Sign(Element h){
        return h.powZn(this.SK).getImmutable();
    }
}
