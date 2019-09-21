package rep.storage

import rep.protos.peer.BlockchainInfo
import _root_.com.google.protobuf.ByteString

class ChainInfoInCache(mda:ImpDataAccess) {
  private var currentheight = 0l
  private var currenttxnumber = 0l
  private var bhash : String = null
  private var bprevhash : String = null
  private var statehash : String= null
  
  initChainInfo
  
  def initChainInfo={
    currentheight = mda.getBlockHeight()
    currenttxnumber = mda.getBlockAllTxNumber()
    val bidx = mda.getBlockIdxByHeight(currentheight)
    if (bidx != null) {
      bhash = bidx.getBlockHash()
      bprevhash = bidx.getBlockPrevHash()
      statehash = bidx.getStateHash()
    }
  }
  
  def getBlockChainInfo(): BlockchainInfo = {
    var rbc = new BlockchainInfo()
    if (bhash != null && !bhash.equalsIgnoreCase("")) {
        rbc = rbc.withCurrentBlockHash(ByteString.copyFromUtf8(bhash))
      } else {
        rbc = rbc.withCurrentBlockHash(ByteString.EMPTY)
      }

      if (bprevhash != null && !bprevhash.equalsIgnoreCase("")) {
        rbc = rbc.withPreviousBlockHash(ByteString.copyFromUtf8(bprevhash))
      } else {
        rbc = rbc.withPreviousBlockHash(ByteString.EMPTY)
      }

      if (statehash != null && !statehash.equalsIgnoreCase("")) {
        rbc = rbc.withCurrentStateHash(ByteString.copyFromUtf8(statehash))
      } else {
        rbc = rbc.withCurrentStateHash(ByteString.EMPTY)
      }
      rbc = rbc.withHeight(currentheight)
      rbc = rbc.withTotalTransactions(currenttxnumber)
    rbc
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