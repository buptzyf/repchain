package rep.storage.test

import rep.app.system.RepChainSystemContext
import rep.network.consensus.common.algorithm.IRandomAlgorithmOfVote
import rep.network.consensus.util.BlockHelp
import rep.proto.rc2.Block
import rep.storage.test.checkTransactionCount.systemName_1
import scalapb.json4s.JsonFormat

object checkBlockHash extends App {
  val systemName = "121000005l35120456.node1"
  val ctx = new RepChainSystemContext(systemName)
  ctx.getSignTool.loadPrivateKey("121000005l35120456.node1", "123", s"${ctx.getCryptoMgr.getKeyFileSuffix.substring(1)}/${ctx.getConfig.getChainNetworkId}/121000005l35120456.node1${ctx.getCryptoMgr.getKeyFileSuffix}")
  val block1_path = "/Users/jiangbuyun/亚丰/2022-12-16下午/block1-1.json"
  val block2_path = "/Users/jiangbuyun/亚丰/2022-12-16下午/block3-1.json"

  val block1 = ReadBlockInJsonFile(block1_path)
  val block2 = ReadBlockInJsonFile(block2_path)

  val vote = new IRandomAlgorithmOfVote
  val candiatetors = vote.candidators(systemName,block1.getHeader.hashPrevious.toStringUtf8,
    ctx.getConfig.getVoteNodeList.toSet,ctx.getHashTool.hash(block1.getHeader.hashPrevious.toStringUtf8))

  System.out.println("test candiatetors="+candiatetors.mkString("-"))

  val header1 = BlockHelp.GetBlockHeaderHash(block1,ctx.getHashTool)
  val header2 = BlockHelp.GetBlockHeaderHash(block2,ctx.getHashTool)


  if(header1.equals(header2)){
    System.out.println(s"hash eq,header1=${header1.toString},header2=${header2.toString}")
  }

  def ReadBlockInJsonFile(path:String): Block = {
    var blk: Block = null

    try {
      val blkJson = scala.io.Source.fromFile(path, "UTF-8")
      val blkStr = try blkJson.mkString finally blkJson.close()
      blk = JsonFormat.fromJsonString[Block](blkStr)
    } catch {
      case e: Exception =>
        e.printStackTrace()
    }
    blk
  }
}
