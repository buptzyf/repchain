package rep.network.consensus.asyncconsensus.common.threshold.threshenc.tpke;


import rep.network.consensus.asyncconsensus.common.threshold.common.*;

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-04-17
 * @category	该类定义TPKE的曲线类型和特定的初始化。
 */
public class Tpke extends IPairing{
    public Tpke(){
        this("ss512");////mnt224,ss512
    }

    public Tpke(String CurvesType){
        super(CurvesType);
    }

    @Override
    protected void Init(){
        String g1_str = "1:UWpnzA782qVUoxRcd4m+d0JcTpZFO0tyJYWo1BRhmjE6eubBplj1WvxdNWH4zqxn6quNGe1AddkgjquGN/QvXQA=";
        this.g1 = this.serializer.Deserialize(g1_str).getImmutable();
        this.g2 = g1.duplicate().getImmutable();
    }

}
