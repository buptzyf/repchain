package rep.network.consensus.asyncconsensus.common.threshold.threshenc.tpke;

import it.unisa.dia.gas.jpbc.Element;
import org.javatuples.Triplet;
import rep.crypto.Sha256;
import rep.network.consensus.asyncconsensus.common.threshold.common.KeyStorager;
import rep.network.consensus.asyncconsensus.common.threshold.common.PairingElementType;
import rep.network.consensus.asyncconsensus.common.threshold.common.PairingLagrange;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-04-17
 * @category	该类实现TPKE的公钥类。
 */

public class TPKEPublicKey extends KeyStorager {
    protected Tpke tpke = null;
    protected int players = 0;
    protected int threshold = 0;
    protected Element VK = null;
    protected Element[] VKs = null;

    public TPKEPublicKey(Tpke tpke,int players, int threshold, Element VK,Element[] VKs){
        this.tpke = tpke;
        this.players = players;
        this.threshold = threshold;
        this.VK = VK;
        this.VKs = VKs;
    }

    public TPKEPublicKey(int players, int threshold, Element VK,Element[] VKs){
        this(new Tpke(),players, threshold, VK, VKs);
    }

    public TPKEPublicKey(HashMap<String,Object> hm){
        this.tpke = new Tpke();
        this.setState(hm);
    }

    public TPKEPublicKey(String fileName){
        this.tpke = new Tpke();
        HashMap<String,Object> hm = this.loadFromFile(fileName);
        this.setState(hm);
    }

    public Tpke getTpke(){return this.tpke;}

    public int getThreshold(){return this.threshold;}

    public int getPlayers(){return this.players;}

    public void saveKey(String fn){
        this.saveToFile(fn,getState());
    }

    public HashMap<String,Object> getState(){
        //序列化数据放到map中输出
        HashMap<String,Object> hm = new HashMap<String,Object>();
        hm.put("VK",this.tpke.getSerializer().SerializeForData(this.VK, PairingElementType.G1,true));
        String[] els = new String[this.VKs.length];
        for(int i = 0; i < this.VKs.length; i++){
            els[i] = this.tpke.getSerializer().SerializeForData(this.VKs[i],PairingElementType.G1,true);
        }
        hm.put("VKS",els);
        hm.put("players",players);
        hm.put("threshold",threshold);
        return hm;
    }

    private void setState(HashMap<String,Object> hm){
        //根据输入的map，设置当前数据
        if(hm == null) return;
        if(hm.get("VK") != null && hm.get("VK") instanceof String){
            this.VK = this.tpke.getSerializer().Deserialize("1:"+hm.get("VK").toString(),true);
        }
        if(hm.get("VKS") != null && hm.get("VKS") instanceof ArrayList){
            ArrayList data = (ArrayList) hm.get("VKS");
            this.VKs = new Element[data.size()];
            for(int i = 0; i < data.size(); i++){
                this.VKs[i] = this.tpke.getSerializer().Deserialize("1:"+data.get(i).toString(),true);
            }
        }
        if(hm.get("players") != null && hm.get("players") instanceof Integer){
            this.players = ((Integer) hm.get("players")).intValue();
        }

        if(hm.get("threshold") != null && hm.get("threshold") instanceof Integer){
            this.threshold = ((Integer) hm.get("threshold")).intValue();
        }
    }


    //本操作只对长度为32字节数组求异或，发生问题返回null结果
    public static byte[] XOR(byte[] x,byte[] y){
        byte[] rb = null;
        if(x == null || y == null) return rb;
        if(x.length != 32 || y.length != 32) return rb;
        rb = new byte[32];
        for(int i = 0; i < 32; i++){
            rb[i] = (byte) (x[i] ^ y[i]);
        }
        return rb;
    }

    public Triplet<Element,byte[],Element> Encrypt(byte[] m){
        if(m.length != 32) return null;
        Element r = this.tpke.getRandom().Random(PairingElementType.ZR).getImmutable();
        Element U = this.tpke.getG1().powZn(r).getImmutable();
        byte[] V = this.XOR(m, Sha256.hash(this.tpke.getSerializer().SerializeForByte(this.VK.powZn(r),PairingElementType.G1,true)));
        Element H = HashH(U,V);
        Element W = H.powZn(r).getImmutable();
        return new Triplet(U,V,W);
    }

    public Element HashH(Element U,byte[] V){
        byte[] Ub = this.tpke.getSerializer().SerializeForByte(U,PairingElementType.G1,true);
        byte[] hb = new byte[Ub.length+V.length];
        System.arraycopy(Ub,0,hb,0,Ub.length);
        System.arraycopy(V,0,hb,Ub.length,V.length);
        Element we = this.tpke.getGroup().getG2().newElementFromHash(hb,0,hb.length).getImmutable();
        return we;
    }

    public boolean VerifyCipherText(Element U,byte[] V,Element W){
        boolean rb = false;
        Element H = this.HashH(U,V);
        if(this.tpke.getGroup().pairing(this.tpke.getG1(),W).isEqual(this.tpke.getGroup().pairing(U,H))){
            rb = true;
        }
        return rb;
    }

    public boolean VerifyShare(int i,Element U_i,Element U,byte[] V,Element W){
        boolean rb = false;
        Element Y_i = this.VKs[i];
        Element p1 = this.tpke.getGroup().pairing(U_i,this.tpke.getG2()).getImmutable();
        Element p2 = this.tpke.getGroup().pairing(U,Y_i).getImmutable();
        if(p1.isEqual(p2)){
            rb = true;
        }
        return rb;
    }

    public byte[] CombineShares(Element U,byte[] V,Element W,HashMap<Integer,Element> shares){
        byte[] rb = null;
        Integer[] S = new Integer[shares.size()];
        shares.keySet().toArray(S);
        Arrays.sort(S);
        for(int i = 0; i < S.length; i++){
            Element value = shares.get(S[i]);
            System.out.println("CombineShares in VerifyShare=j:"+S[i]+this.VerifyShare(S[i],value,U,V,W));
        }

        System.out.println("s="+Arrays.deepToString(S));

        Element[] els = PairingLagrange.computeLagrangePow(this.tpke.getONE(),shares,S);
        Element result = null;
        for(int i = 0; i < els.length; i++){
            if(result == null){
                result = els[i].getImmutable();
            }else{
                result = result.mul(els[i]).getImmutable();
            }
        }

        byte[] b = Sha256.hash(this.tpke.getSerializer().SerializeForByte(result,PairingElementType.G1,true));
        rb = this.XOR(b,V);

        return rb;
    }


}
