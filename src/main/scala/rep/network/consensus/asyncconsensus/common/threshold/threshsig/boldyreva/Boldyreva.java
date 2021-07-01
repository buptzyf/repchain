package rep.network.consensus.asyncconsensus.common.threshold.threshsig.boldyreva;

import rep.network.consensus.asyncconsensus.common.threshold.common.*;


/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-04-17
 * @category	该类定义了BLS曲线类型和特有的初始化。
 */
public class Boldyreva extends IPairing {

    public Boldyreva(){
        this("MNT224");////mnt224,ss512
    }

    public Boldyreva(String CurvesType){
        super(CurvesType);
    }

    @Override
    protected void Init(){
        this.g1 = this.serializer.Deserialize("1:Hw8fQ59CfkFyNR2rGK5BLWSfwfxAlFMA89IkTAE=").getImmutable();
        this.g2 = this.serializer.Deserialize("2:Plp1Jb6RDCvLNI6RGCQAuZghgJcwml/93322Nh0sZdVnwIFKYsOxxgFtg416U2vl/RIUfPT0ShEVekx6xXYIMhoV+CTwlViWtd7hQE//azdpwtOFAQ==").getImmutable();
    }

}
