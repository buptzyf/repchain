package rep.network.consensus.asyncconsensus.common.threshold.threshenc.experiment;

import it.unisa.dia.gas.jpbc.Element;
import rep.network.consensus.asyncconsensus.common.threshold.threshenc.tpke.Tpke;
import rep.network.consensus.asyncconsensus.common.threshold.common.PairingElementType;
import rep.network.consensus.asyncconsensus.common.threshold.common.PairingSerializer;

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-04-17
 * @category	该类测试配对元素的序列化和反序列化。
 */

public class TpkeTest {
    public static void test1(){
        Tpke tpke = new Tpke();
        PairingSerializer serializer = tpke.getSerializer();
        Element g1 = tpke.getGroup().getG1().newRandomElement();
        String str = serializer.Serialize(g1, PairingElementType.G1);
        Element tmpg1 = serializer.Deserialize(str);
        System.out.println("test1 serializable in compressed:");
        System.out.println("java str="+str);
        System.out.println("Serialize and Deserialize result:"+g1.isEqual(tmpg1));
    }

    public static void test3(){
        Tpke tpke = new Tpke();
        PairingSerializer serializer = tpke.getSerializer();
        Element g1 = tpke.getGroup().getG1().newRandomElement();
        String str = serializer.Serialize(g1, PairingElementType.G1,false);
        Element tmpg1 = serializer.Deserialize(str,false);
        System.out.println("test3 serializable in not compressed:");
        System.out.println("java str="+str);
        System.out.println("Serialize and Deserialize result:"+g1.isEqual(tmpg1));
    }

    public static void test2(){
        String src = "1:UWpnzA782qVUoxRcd4m+d0JcTpZFO0tyJYWo1BRhmjE6eubBplj1WvxdNWH4zqxn6quNGe1AddkgjquGN/QvXQA=";
        Tpke tpke = new Tpke();
        PairingSerializer serializer = tpke.getSerializer();
        Element g1 = serializer.Deserialize(src);
        String str = serializer.Serialize(g1, PairingElementType.G1);

        System.out.println("test2 python compare java:");
        System.out.println("python src="+src);
        System.out.println("java   new="+str);
    }

    public static void main(String[] args){
       test1();
        test2();
        test3();
    }
}
