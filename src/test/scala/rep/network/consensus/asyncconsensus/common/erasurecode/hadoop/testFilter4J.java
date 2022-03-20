package rep.network.consensus.asyncconsensus.common.erasurecode.hadoop;

import com.github.mgunlogson.cuckoofilter4j.*;
import com.google.common.hash.Funnels;
import scala.Array;

import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;


public class testFilter4J {

    public static void   test(Integer datalen,String filename){
        CuckooFilter<String> filter = new CuckooFilter.Builder(Funnels.stringFunnel(Charset.forName("UTF-8")), datalen).withFalsePositiveRate(0.01).withHashAlgorithm(Utils.Algorithm.sha256).build();
        long start = System.currentTimeMillis();
        for(int i = 0 ; i < datalen; i++){
            filter.put(java.util.UUID.randomUUID().toString());
        }
        long end = System.currentTimeMillis();
        System.out.println("Put Element to CuckooFilter,ready key="+(datalen/10)+"Items,put keys="+filter.getCount()+"Items,storage size="+(filter.getStorageSize()/8/1000)+"KB"+
                ",LoadFactor="+filter.getLoadFactor()+",ActualCapacity="+(filter.getActualCapacity()/8/1000)+"KB"+",spent time="+(end - start)+"ms");
        saveToFile(filter,filename);
    }

    public static void saveToFile(CuckooFilter<String> filter,String fn){
        long start = System.currentTimeMillis();
        FileOutputStream fs = null;
        ObjectOutputStream os = null;
        try{
            fs = new FileOutputStream(fn);
            os = new ObjectOutputStream(fs);
            os.writeObject(filter);
            os.flush();
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            if(os != null){
                try {
                    os.close();
                }catch (Exception el){}
            }
            if(fs != null){
                try {
                    fs.close();
                }catch (Exception el){}
            }
        }
        long end = System.currentTimeMillis();
        System.out.println("Save CuckooFilter to file,file name="+fn+",spent time="+(end - start)+"ms");
    }

    public static void main(String[] args) {
        test(100000,"/Users/jiangbuyun/foo10.ser");
        test(1000000,"/Users/jiangbuyun/foo100.ser");
        //test(10000000,"/Users/jiangbuyun/foo1000.ser");
        //test(100000000,"/Users/jiangbuyun/foo10000.ser");
    }

}
