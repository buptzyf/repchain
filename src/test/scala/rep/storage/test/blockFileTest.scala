package rep.storage.test

import rep.app.conf.RepChainConfig
import rep.app.system.RepChainSystemContext
import rep.network.consensus.util.BlockHelp

object blockFileTest extends App {
  val systemName = "121000005l35120456.node1"
  //val config = new RepChainConfig(systemName)
  val ctx = new RepChainSystemContext (systemName)
  val bs = ctx.getBlockStorager
  val b = BlockHelp.CreateGenesisBlock(ctx.getConfig)
  bs.saveBlock(Some(b))
  bs.getBlockByHeight(1l)
  System.out.println("ok")
}
