package rep.network.consensus.asyncconsensus.common.erasurecode

import rep.network.consensus.asyncconsensus.common.erasurecode.hadoop.ErasureCodeOfHadoop
import rep.protos.peer.DataOfStripe

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-02-13
 * @category	包装实现纯Java语言的纠删码，底层调用开源的HDFS的纠删码实现，不足的地方是纯Java语言的纠删码实现性能是C语言实现的1/4，
 *          目前暂时采用Java，未来需要使用C语言版来替换。
 */

class ImpErasureCode(numOfReconstruct:Int,numOfParity:Int) extends IErasureCode(numOfReconstruct,numOfParity) {

  override def encode(data: Array[Byte]): Array[DataOfStripe] = {
    val encoder = new ErasureCodeOfHadoop(this.numOfReconstruct,this.numOfParity)
    encoder.encodeToStripes(data)
  }

  override def decode(encodeData: Array[DataOfStripe]): Array[Byte] = {
    val decoder = new ErasureCodeOfHadoop(this.numOfReconstruct,this.numOfParity)
    decoder.decode(encodeData)
  }

}
