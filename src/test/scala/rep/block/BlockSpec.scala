package rep.block

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike, Matchers}
import rep.app.conf.RepChainConfig
import rep.app.system.RepChainSystemContext
import rep.network.consensus.common.MsgOfConsensus.GenesisBlock
import rep.network.module.cfrd.{CFRDActorType, ModuleManagerOfCFRD}
import rep.network.tools.PeerExtension

class BlockSpec(_system: ActorSystem) extends TestKit(_system) with Matchers with FunSuiteLike with BeforeAndAfterAll {
  def this() = this(
    ActorSystem("BlockSpec", new RepChainConfig("121000005l35120456.node1").getSystemConf)
  )

  val ctx : RepChainSystemContext = new RepChainSystemContext("121000005l35120456.node1")
  val pe = PeerExtension(system)
  pe.setRepChainContext(ctx)
  val moduleManager = system.actorOf(ModuleManagerOfCFRD.props("modulemanager", false), "modulemanager")

  override def afterAll: Unit = {
    shutdown(system)
  }

  val probe = TestProbe()

  test("Block 发送创世消息，建立创世块，预执行区块，发出确认消息，检查确认块，发送存储模块，保存区块") {
    Thread.sleep(3000)
    probe.send(pe.getActorRef(CFRDActorType.ActorType.gensisblock), GenesisBlock)
    Thread.sleep(5000)
    val search = ctx.getBlockSearch
    val chain = search.getChainInfo
    chain.height should be (1)
  }


}
