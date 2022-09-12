package rep.accumulator

import rep.accumulator.CreateTestTransactionService.tx_data
import rep.accumulator.vectorCommitment_test.tx_service
import rep.accumulator.verkle.util
import rep.accumulator.verkle.util.verkleTool
import rep.crypto.Sha256

import java.io.PrintWriter
import java.math.BigInteger
import java.util
import scala.collection.mutable.ArrayBuffer

object Verkle_test extends App {
  val tx_service = new CreateTestTransactionService
  val ctx = tx_service.ctx
  val hash_tool = new Sha256(ctx.getCryptoMgr.getInstance)

  test_Tree

  def test_Tree:Unit={
    val t = getTransaction(50)
    val ts = handleTransaction(t)
    val root = ctx.getVerkleNodeBuffer.readMiddleNode(null)
    for(i<-0 to ts.length-1){
      System.out.println(s"id=${verkleTool.getKey(ts(i)._1)},prime=${ts(i)._3}")
      root.addState(ts(i)._1,ts(i)._2,ts(i)._3)
    }
    val str = root.middleToString
    val pw = new PrintWriter(s"repchaindata/verkle-tree.txt", "UTF-8")
    pw.write(str)
    pw.flush()
    pw.close()
  }

  private def handleTransaction(data:Array[tx_data]):Array[(Array[Int],Array[Byte],BigInteger)]={
    val sb = new ArrayBuffer[(Array[Int],Array[Byte],BigInteger)]()
    data.foreach(d=>{
      sb += Tuple3(verkle.util.verkleTool.getIndex(hash_tool.hash(d.tx)),d.tx,d.prime)
    })
    sb.toArray
  }

  def getTransaction(count: Int): Array[tx_data] = {
    val r = new Array[tx_data](count)
    for (i <- 0 to count - 1) {
      r(i) = tx_service.readTx
    }
    r
  }
}
