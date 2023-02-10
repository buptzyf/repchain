package rep.storage.test

import rep.app.system.RepChainSystemContext
import rep.storage.chain.block.BlockSearcher
import scala.util.control.Breaks.{break, breakable}

object checkTransactionCount extends App {
  val systemName_1 = "121000005l35120456.node1"
  val systemName_2 = "12110107bi45jh675g.node2"

  val ctx_1 = new RepChainSystemContext(systemName_1)
  val ctx_2 = new RepChainSystemContext(systemName_2)

  val maxHeight = 35295
  val start = 1

  val sr_1: BlockSearcher = ctx_1.getBlockSearch
  val sr_2: BlockSearcher = ctx_2.getBlockSearch

  var total_1 = 0
  var total_2 = 0

  breakable(
    for(i<-start to maxHeight){
      val blockIndex_1 = sr_1.getBlockIndexByHeight(Some(i.toLong)).get
      val blockIndex_2 = sr_2.getBlockIndexByHeight(Some(i.toLong)).get

      val block_1 = sr_1.getBlockByHeight(i.toLong).get
      val block_2 = sr_2.getBlockByHeight(i.toLong).get

      if(block_1.transactions.length != block_2.transactions.length){
        System.err.println(s"Total number of transactions in Block is not equal，height=${i},count_1=${block_1.transactions.length} ," +
          s"count_2=${block_2.transactions.length},Previous totals=${total_1},${total_2} ")
        break
      }

      if (block_1.transactions.length != blockIndex_1.getTransactionSize ||
        block_2.transactions.length != blockIndex_2.getTransactionSize) {
        System.err.println(s" transactions of block is not equal transactions of blockIndex，" +
          s"height=${i},count_1=${blockIndex_1.getTransactionSize}, ${block_1.transactions.length};" +
          s"count_2=${blockIndex_2.getTransactionSize},${block_2.transactions.length};Previous totals=${total_1},${total_2} ")
        break
      }

      if(total_1 != total_2){
        System.err.println(s"Total number of transactions is not equal，height=${i},count_1=${blockIndex_1.getTransactionSize} ," +
          s"count_2=${blockIndex_2.getTransactionSize},Previous totals=${total_1},${total_2} ")
        break
      }else{
        total_1 += blockIndex_1.getTransactionSize
        total_2 += blockIndex_2.getTransactionSize
        System.out.println(s"block height=${i},  total_1=${total_1},total_2=${total_2},current transaction size=${blockIndex_1.getTransactionSize}")
      }
    }
  )

}
