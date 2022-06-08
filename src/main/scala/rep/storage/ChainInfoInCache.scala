package rep.storage


import rep.proto.rc2.BlockchainInfo
import rep.storage.chain.block.BlockSearcher

class ChainInfoInCache(mda:BlockSearcher) {
  private var currentheight = 0l
  private var currenttxnumber = 0l
  private var bhash : String = null
  private var bprevhash : String = null
  private var statehash : String= null
  
  initChainInfo
  
  def initChainInfo={
    val lstInfo = mda.getLastChainInfo
    currentheight = lstInfo.height
    currenttxnumber = lstInfo.txCount
    val bidx = mda.getBlockIndexByHeight(Some(currentheight)).getOrElse(null)
    if (bidx != null) {
      bhash = bidx.getHash
      bprevhash = bidx.getPreHash
      statehash = ""
    }
  }
  
  def getBlockChainInfo(): BlockchainInfo = {
    mda.getChainInfo
  }
  
  def setHeight(h:Long)={
    this.currentheight = h
  }
  
  def setTXNumber(n:Long)={
    this.currenttxnumber = n
  }
  
  def setBlockHash(hash:String)={
    this.bhash = hash
  }
  
  def setPrevBlockHash(phash:String)={
    this.bprevhash = phash
  }
  
  def setBlockStateHash(state:String)={
    this.statehash = state
  }
    
}