package rep.network.consensus.asyncconsensus.common.erasurecode.hadoop;

import rep.network.consensus.asyncconsensus.common.erasurecode.ErasureCodeFactory;
import rep.network.consensus.asyncconsensus.common.erasurecode.IErasureCode;
import rep.network.consensus.asyncconsensus.common.merkle.MerkleTree;
import rep.protos.peer.DataOfStripe;


import java.util.Random;

public class testErasureCodeOfHadoop {

    private static DataOfStripe[] copyOut(DataOfStripe[] out){
        DataOfStripe[] rds = new DataOfStripe[out.length];
        for(int i = 0; i < out.length; i++){
            rds[i] = new DataOfStripe(out[i].version(),out[i].serial(),out[i].stripe(),  out[i].rootHash(), out[i].branch(),out[i].unknownFields());
        }
        return rds;
    }

    private static void erasureData(DataOfStripe[] out1,int dataLen){
        int loop = out1.length - dataLen;
        int len = out1.length;
        Random random=new Random();
        for(int i = 0; i < loop; i++){
            int r = Math.abs(random.nextInt()) % len;
            out1[r] = null;
        }
    }

    //解码测试例：完整解码数据输入
    protected static void example1(IErasureCode erasure,DataOfStripe[] out) throws Exception {
        DataOfStripe[] out1 = copyOut(out);
        long start = System.currentTimeMillis();
        String srcOfDecode = new String(erasure.decode(out1),"UTF-8");
        long end = System.currentTimeMillis();
        System.out.println("example1 decode time:"+(end - start)+"ms");
        //System.out.println("example1:"+srcOfDecode);
    }

    //解码测试例：删除开始末尾部分数据
    protected static void example2(IErasureCode erasure,DataOfStripe[] out,int dataLen) throws Exception {
        DataOfStripe[] out1 = copyOut(out);
        erasureData(out1,dataLen);

        long start = System.currentTimeMillis();
        String srcOfDecode = new String(erasure.decode(out1),"UTF-8");
        long end = System.currentTimeMillis();
        System.out.println("example2 decode time:"+(end - start)+"ms");
        //System.out.println("example2:"+srcOfDecode);
    }





    private static String generateBigData(int num,boolean isUseDefault){
        String str="abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        if(!isUseDefault){
            Random random=new Random();
            StringBuffer sb = new StringBuffer();
            for(int i = 0; i < num; i++){
                int number=random.nextInt(62);
                sb.append(str.charAt(number));
            }
            str = sb.toString();
        }
        return str;
    }

    private static void verifyBrach(DataOfStripe[] out){
        for(int i = 0; i < out.length; i++){
            long start = System.currentTimeMillis();
            boolean b = MerkleTree.merkleVerify(out.length,out[i]);
            long end = System.currentTimeMillis();
            System.out.println("verifyBrach time:"+(end - start)+"ms");
            System.out.println("verify merkle,index="+i+",result:"+b);
        }
    }

    public static void main(String[] args){
        int dataLen = 180;
        int parityLen = 70;
        IErasureCode erasure =  ErasureCodeFactory.generateErasureCoder(dataLen,parityLen,1);
        //String src = "111111222222333334444555666777788889999000000aaaaaabbbbbbccccccccdddddeeeffg";
        String src =  generateBigData(512000,false);
        try {
            long start = System.currentTimeMillis();
            DataOfStripe[] out = erasure.encode(src.getBytes("UTF-8"));
            long end = System.currentTimeMillis();
            System.out.println("encode time:"+(end - start)+"ms");
            if(out != null){
                //System.out.println("src     :"+src);
                verifyBrach(out);
                example1(erasure,out);
                example2(erasure,out,dataLen);
                System.out.println("finish");
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
