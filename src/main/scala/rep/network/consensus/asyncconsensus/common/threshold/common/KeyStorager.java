package rep.network.consensus.asyncconsensus.common.threshold.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.util.Base64;
import java.util.HashMap;

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-04-17
 * @category	该类包括密钥保存到磁盘和从磁盘导入密钥。
 */

public class KeyStorager {
    public static  String convertHashMapToJSonStr(HashMap<String,Object> hm){
        String rstr = "";
        ObjectMapper mapper = new ObjectMapper();
        try {
            rstr = mapper.writeValueAsString(hm);
        }catch (Exception e) {
            e.printStackTrace();
        }
        return rstr;
    }

    public static  HashMap<String,Object> convertJSonStrToHashMap(String jsonStr){
        HashMap<String, Object> map = new HashMap<String, Object>();

        ObjectMapper mapper = new ObjectMapper();
        try{
            map = mapper.readValue(jsonStr, new TypeReference<HashMap<String, Object>>(){});
        }catch (Exception e){
            e.printStackTrace();
        }
        return map;
    }

    protected static HashMap<String,Object> loadFromFile(String fn) {
        HashMap<String,Object> hm = null;
        FileReader reader = null;
        BufferedReader br = null;
        try {
            reader = new FileReader(fn);
            br = new BufferedReader(reader);
            StringBuffer content = new StringBuffer();
            String line ;
            while ((line = br.readLine()) != null) {
                content.append(line);
            }
            String jstr = new String(Base64.getDecoder().decode(content.toString()),"UTF-8");
            hm = convertJSonStrToHashMap(jstr);
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            if(br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if(reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return hm;
    }

    protected boolean saveToFile(String fn,HashMap<String,Object> hm){
        boolean rb = false;
        FileWriter writer = null;
        BufferedWriter out = null;
        try{
            String jstr = convertHashMapToJSonStr(hm);
            String encodeStr = Base64.getEncoder().encodeToString(jstr.getBytes("UTF-8"));
            File wr = new File(fn);
            wr.createNewFile();
            writer = new FileWriter(wr);
            out = new BufferedWriter(writer);
            out.write(encodeStr);
            out.flush();
            rb = true;
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            if(out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if(writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return rb;
    }
}
