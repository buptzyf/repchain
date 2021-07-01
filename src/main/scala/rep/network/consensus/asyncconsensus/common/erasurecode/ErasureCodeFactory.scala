package rep.network.consensus.asyncconsensus.common.erasurecode

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-02-13
 * @category	纠删码对象的生产工厂，根据需要生成不同的纠删码类。
 */
object ErasureCodeFactory {
  def generateErasureCoder(numOfReconstruct:Int,numOfParity:Int, erasureCodeType:Int=1):IErasureCode={
    if(erasureCodeType == 1){
      var coder = new ImpErasureCode(numOfReconstruct,numOfParity)
      coder
    }else{
      var coder = new ImpErasureCode(numOfReconstruct,numOfParity)
      coder
    }
  }
}
