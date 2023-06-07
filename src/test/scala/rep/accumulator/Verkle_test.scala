package rep.accumulator


import org.apache.commons.codec.BinaryEncoder
import rep.accumulator.CreateTestTransactionService.tx_data
import rep.accumulator.verkle.VerkleTreeType
import rep.accumulator.verkle.util.verkleTool
import rep.crypto.Sha256
import rep.utils.SerializeUtils

import java.math.BigInteger
import scala.collection.mutable.ArrayBuffer

object Verkle_test extends App {
  val tx_service = new CreateTestTransactionService
  val ctx = tx_service.ctx
  val hash_tool = new Sha256(ctx.getCryptoMgr.getInstance)

  test_Tree
  //test_sha
  def test_sha:Unit={
    val str = "adf-sle-"
    val str1 = "adf-sle-"+"sdd"
    //val str_h = ctx.getHashTool.hashstr(str.getBytes)
    //val str1_h = ctx.getHashTool.hashstr(str1.getBytes)
    val str_h = org.apache.commons.codec.binary.BinaryCodec.toAsciiString(str.getBytes)
    val str1_h = org.apache.commons.codec.binary.BinaryCodec.toAsciiString(str1.getBytes)
    System.out.println(s"str=${str},str_h=${str_h},str1=${str1},str1_h=${str1_h}")
  }

  def test_Tree: Unit = {
    val t = getTransaction(60)
    val ts = handleTransaction(t)
    val root = ctx.getVerkleTreeNodeBuffer(VerkleTreeType.TransactionTree).readMiddleNode(null)
    for (i <- 0 to 10) {
      System.out.println(s"serial=${i} id=${verkleTool.getKey(ts(i)._1)},prime=${ts(i)._3}")
      root.addState(ts(i)._1, ts(i)._2, ts(i)._3)
      val tmpNode = ctx.getVerkleTreeNodeBuffer(VerkleTreeType.TransactionTree).readMiddleNode(null)
      System.out.println(tmpNode.middleToString)
    }

    for (i <- 3 to 6) {
      val d = ts(i)
      val proofs = root.getProofs(d._1, d._2, d._3)
      System.out.println("proof length=" + SerializeUtils.serialise(proofs).length)
      val b = root.verifyProofs(d._3, proofs)
      if (b) {
        System.out.println(s"serial=${i} id=${verkleTool.getKey(d._1)},verify member proof ok")
      } else {
        System.out.println(s"serial=${i} id=${verkleTool.getKey(d._1)},verify member proof false")
      }
    }

    for (i <- 7 to 10) {
      val d = ts(i)
      val proofs = root.getProofs(d._1, d._2, d._3)
      System.out.println("proof length=" + SerializeUtils.serialise(proofs).length)
      val b = root.verifyProofs(d._3, proofs)
      if (b) {
        System.out.println(s"serial=${i} id=${verkleTool.getKey(d._1)},verify Nonmember proof ok")
      } else {
        System.out.println(s"serial=${i} id=${verkleTool.getKey(d._1)},verify Nonmember proof false")
      }
    }

    /*val str = root.middleToString
    val pw = new PrintWriter(s"repchaindata/verkle-tree.txt", "UTF-8")
    pw.write(str)
    pw.flush()
    pw.close()*/
  }

  private def handleTransaction(data: Array[tx_data]): Array[(Array[Int], Array[Byte], BigInteger)] = {
    val sb = new ArrayBuffer[(Array[Int], Array[Byte], BigInteger)]()
    data.foreach(d => {
      sb += Tuple3(verkle.util.verkleTool.getIndex(hash_tool.hash(d.tx)), d.tx, d.prime)
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
