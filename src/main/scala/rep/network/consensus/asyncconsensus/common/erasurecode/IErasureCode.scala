package rep.network.consensus.asyncconsensus.common.erasurecode

import com.google.protobuf.ByteString
import rep.protos.peer.DataOfStripe


/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-02-13
 * @category	包装纠删码的接口，未来可能有多种纠删码的实现。
 */
abstract class IErasureCode(numOfReconstruct:Int,numOfParity:Int) {
   def encode(data:Array[Byte]):Array[DataOfStripe]
   def decode(encodeData:Array[DataOfStripe]):Array[Byte]
}
