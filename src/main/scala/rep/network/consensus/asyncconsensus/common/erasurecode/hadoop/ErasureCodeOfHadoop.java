package rep.network.consensus.asyncconsensus.common.erasurecode.hadoop;

import com.google.protobuf.ByteString;
import org.apache.hadoop.io.erasurecode.ErasureCoderOptions;
import org.apache.hadoop.io.erasurecode.rawcoder.RSRawDecoder;
import org.apache.hadoop.io.erasurecode.rawcoder.RSRawEncoder;
import rep.network.consensus.asyncconsensus.common.merkle.MerkleTree;
import rep.protos.peer.DataOfStripe;
import scala.collection.Seq;
import scalapb.UnknownFieldSet;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-03-1
 * @category	包装实现纯Java语言的纠删码，底层调用开源的HDFS的纠删码实现，不足的地方是纯Java语言的纠删码实现性能是C语言实现的1/4，
 *          目前暂时采用Java，未来需要使用C语言版来替换。
 */

public class ErasureCodeOfHadoop {
    private ErasureCoderOptions opt = null;

    public ErasureCodeOfHadoop(int dataUnit,int parityUnit){
        this.opt = this.createOption(dataUnit,parityUnit);
    }

    //对一个字符串进行编码
    public byte[][] encode(String enData) throws Exception {
        return this.encode(enData.getBytes("UTF-8"));
    }

    //对字节数组进行编码
    public byte[][] encode(byte[] enData) throws Exception {
        RSRawEncoder encoder = this.createEncoder(this.opt);

        byte[][] input = this.preparedEndata(enData);
        byte[][] output = new byte[this.opt.getNumParityUnits()][input[0].length];

        encoder.encode(input,output);

        byte[][] rbs = new byte[this.opt.getNumAllUnits()][input[0].length];
        System.arraycopy(input, 0, rbs, 0, this.opt.getNumDataUnits());
        System.arraycopy(output, 0, rbs, this.opt.getNumDataUnits(), this.opt.getNumParityUnits());

        return rbs;
    }

    public DataOfStripe[] encodeToStripes(String enData) throws Exception{
        return this.encodeToStripes(enData.getBytes("UTF-8"));
    }

    public DataOfStripe[] encodeToStripes(byte[] enData) throws Exception{
        byte[][] ecb = encode(enData);

        ByteString[] tree = MerkleTree.createMerkleTree(this.preparedDataOfMerkle(ecb));

        DataOfStripe[] rds = new DataOfStripe[this.opt.getNumAllUnits()];
        for(int i = 0; i < this.opt.getNumAllUnits(); i++){
            Seq<ByteString> branch = MerkleTree.getMerkleBranch(i,tree);
            DataOfStripe ds = new DataOfStripe(0, i,this.opt.getNumAllUnits(), ByteString.copyFrom(ecb[i]), tree[1],  branch, UnknownFieldSet.empty());
            rds[i] = ds;
        }
        /*for(int i = this.opt.getNumDataUnits(); i < this.opt.getNumAllUnits(); i++){
            DataOfStripe ds = new DataOfStripe(0,i, ByteString.copyFrom(ecb[i]),ByteString.EMPTY, (Seq<ByteString>) Seq.canBuildFrom(), UnknownFieldSet.empty());
            rds[i] = ds;
        }*/
        return rds;
    }

    public String decodeToString(DataOfStripe[] dss) throws Exception {
        return new String(decode(dss),"UTF-8");
    }

    public byte[] decode(DataOfStripe[] dss) throws Exception{
        if(dss == null){
            throw new Exception("DataOfStripe is null");
        }else if(dss.length <= 0 ){
            throw new Exception("DataOfStripe's length is zero");
        }else{
            RSRawDecoder decoder = this.createDecoder(this.opt);

            int dataLen = this.getDataLength(dss);
            int erasureLen = this.getErasureNumber(dss);

            if(erasureLen > this.opt.getNumParityUnits()){
                throw new Exception("erasure code data grant than the parity length.");
            }

            //准备解码的数据结构
            byte[][] input = new byte[this.opt.getNumAllUnits()][dataLen];
            int[] erasureIndex = new int[erasureLen];
            byte[][] output = new byte[erasureLen][dataLen];


            List<Integer> serialLs = new ArrayList<Integer>();
            for(int i = 0; i < dss.length; i++){
                DataOfStripe ds = dss[i];
                if(ds != null){
                    serialLs.add(ds.serial());
                    byte[] data = ds.stripe().toByteArray();
                    input[ds.serial()] = data;
                }
            }

            //检查无效的data
            int j = 0;
            for(int i = 0;  i < this.opt.getNumAllUnits(); i++){
                if(!serialLs.contains(i)){
                    input[i] = null;
                    erasureIndex[j++] = i;
                }
            }

            decoder.decode(input,erasureIndex,output);

            //还原数据到input
            for(int i = 0; i < erasureIndex.length; i++){
                input[erasureIndex[i]] = new byte[dataLen];
                System.arraycopy(output[i],0,input[erasureIndex[i]],0,dataLen);
            }

            byte[][] rbs = new byte[this.opt.getNumDataUnits()][dataLen];
            System.arraycopy(input,0,rbs,0,this.opt.getNumDataUnits());
            return this.restoreEndata(rbs);
        }
    }

    private ErasureCoderOptions createOption(int dataUnit,int parityUnit){
        return new ErasureCoderOptions(dataUnit,parityUnit);
    }

    private RSRawEncoder createEncoder(ErasureCoderOptions opt){
        RSRawEncoder encoder = new RSRawEncoder(opt);
        return encoder;
    }

    private RSRawDecoder createDecoder(ErasureCoderOptions opt){
        RSRawDecoder decoder = new RSRawDecoder(opt);
        return decoder;
    }

    //预处理待编码的字节数组，使得待处理数组分割成为相同长度的数组，不足时进行填充
    private byte[][] preparedEndata(byte[] enData) throws Exception{
        if(enData == null){
            throw new Exception("encode data is null");
        }else {
            if (enData.length == 0) {
                throw new Exception("encode data length equal zero");
            } else {
                int srcLen = enData.length;
                int padLen = this.opt.getNumDataUnits() - (srcLen % this.opt.getNumDataUnits());
                int padValue = this.opt.getNumDataUnits() - padLen;
                byte[] middleData = null;
                middleData = new byte[srcLen + padLen];
                System.arraycopy(enData, 0, middleData, 0, srcLen);
                for(int i = srcLen; i < middleData.length; i++){
                    middleData[i] = (byte) (padValue);
                }
                ByteBuffer bb = ByteBuffer.wrap(middleData);
                int blockLen = middleData.length / this.opt.getNumDataUnits();
                byte[][] rbs = new byte[this.opt.getNumDataUnits()][blockLen];
                for(int i= 0; i < this.opt.getNumDataUnits(); i++){
                    bb.get(rbs[i],0,blockLen);
                }
                return rbs;
            }
        }
    }

    //解码之后，恢复原始数据，删除填充字节
    private byte[] restoreEndata(byte[][] data) throws Exception{
        if(data == null) {
            throw new Exception("restore erasure data is null");
        }else if(data.length < this.opt.getNumDataUnits()){
            throw new Exception("restore erasure data length error");
        }else{
            int dataLength = data[0].length;
            byte lastByte = data[this.opt.getNumDataUnits()-1][dataLength-1];
            int padLen = this.opt.getNumDataUnits() - (int)lastByte;
            byte[] rbs = new byte[(this.opt.getNumDataUnits()-1)*dataLength+(dataLength-padLen)];
            for(int i = 0; i < this.opt.getNumDataUnits(); i++){
                byte[] tmp = data[i];
                if(i == (this.opt.getNumDataUnits()-1)){
                    System.arraycopy(tmp,0,rbs,i*dataLength,dataLength-padLen);
                }else{
                    System.arraycopy(tmp,0,rbs,i*dataLength,dataLength);
                }
            }
            return rbs;
        }
    }

    private int getDataLength(DataOfStripe[] dss){
        int len = 0;
        for(int i = 0; i < dss.length; i++){
            DataOfStripe ds = dss[i];
            if(ds != null){
                len = ds.stripe().toByteArray().length;
                break;
            }
        }
        return len;
    }

    private int getErasureNumber(DataOfStripe[] dss){
        int counter = 0;
        for(int i = 0; i < dss.length; i++){
            DataOfStripe ds = dss[i];
            if(ds == null){
                counter++;
            }
        }
        return counter;
    }

    private ByteString[] preparedDataOfMerkle(byte[][] data)throws Exception {
        if(data == null) throw new Exception("Merkle data is null.");
        if(data.length == this.opt.getNumAllUnits()){
            ByteString[] bs = new ByteString[this.opt.getNumAllUnits()];
            for(int i = 0; i < data.length; i++){
                bs[i] = ByteString.copyFrom(data[i]);
            }
            return bs;
        }else{
            throw new Exception("merkle data is not equal all units.");
        }
    }

}
