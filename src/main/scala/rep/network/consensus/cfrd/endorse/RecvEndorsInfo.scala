package rep.network.consensus.cfrd.endorse

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import akka.util.ByteString
import rep.protos.peer.Block

class RecvEndorsInfo {
  var block : Block = null
  var verifyBlockSign : AtomicBoolean = new AtomicBoolean(false)
  var verifyTran : AtomicBoolean = new AtomicBoolean(false)
  var checkRepeatTrans : AtomicInteger = new AtomicInteger(0)
  var preload:AtomicBoolean = new AtomicBoolean(false)

  def clean={
    block = null
    verifyBlockSign.set(false)
    verifyTran.set(false)
    checkRepeatTrans.set(0)
    preload.set(false)
  }

}
