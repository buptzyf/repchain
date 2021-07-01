package rep.network.consensus.asyncconsensus.common.threshold.common;

import java.io.File;

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-04-17
 * @category	该类定义配对曲线的类型，以及设置对应类型曲线参数的位置。
 */

public class ParingCurves {
    /**
     * 根据参数名称获取曲线参数文件类型
     * 参数名称有：{'SS512', 'SS1024', 'MNT159', 'MNT201', 'MNT224', 'BN254' }
     */
    public static String getCurvesParams(String paramName){
        String mainPath = System.getProperty("user.dir") + File.separator + "params"+File.separator+"curves"+File.separator;
        if(paramName.equalsIgnoreCase("SS512")){
            mainPath = mainPath + "a.properties";
        }else if(paramName.equalsIgnoreCase("SS1024")){
            mainPath = mainPath + "a1.properties";
        }else if(paramName.equalsIgnoreCase("MNT159")){
            mainPath = mainPath + "d159.properties";
        }else if(paramName.equalsIgnoreCase("MNT201")){
            mainPath = mainPath + "d201.properties";
        }else if(paramName.equalsIgnoreCase("MNT224")){
            mainPath = mainPath + "d224.properties";
        }
        return mainPath;
    }

}
