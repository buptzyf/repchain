package rep.network.consensus.asyncconsensus

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestKit, TestProbe}
import org.json4s.{DefaultFormats, jackson}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType

import scala.concurrent.duration._
import rep.network.consensus.asyncconsensus.protocol.binaryagreement.BinaryAgreement.{ResultOfBA, StartBinaryAgreement}
import rep.network.consensus.asyncconsensus.protocol.binaryagreement.BinaryAgreementInActor
import rep.network.consensus.asyncconsensus.protocol.commoncoin.CommonCoinInActor
import rep.protos.peer.{ResultOfCommonCoin, StartCommonCoin}

class BinaryAgreementTestActor (_system: ActorSystem)
  extends TestKit(_system) with Matchers with FlatSpecLike with BeforeAndAfterAll {



  def this() = this(ActorSystem("ProvableReliableBroadcastSpec", new ClusterSystem("121000005l35120456.node1", InitType.MULTI_INIT, false).getConf))

  override def afterAll: Unit = {
    shutdown(system)
  }

  implicit val serialization = jackson.Serialization
  // or native.Serialization
  implicit val formats = DefaultFormats

  "ProvableReliableBroadcastSpec" should "assert binary agreement value" in {
    val sysName = "121000005l35120456.node1"
    //val dbTag = "121000005l35120456.node1"
    //val pm = system.actorOf(ModuleManagerOfDUMBO.props("modulemanager", sysName, false, false, false), "modulemanager")

    var probes = new Array[TestProbe](4)
    for(i <- 0 to 3){
      probes(i) = TestProbe()
    }

    var nodelist : Array[String] = new Array[String] (4)
    nodelist(0) = "121000005l35120456.node1"
    nodelist(1) = "12110107bi45jh675g.node2"
    nodelist(2) = "122000002n00123567.node3"
    nodelist(3) = "921000005k36123789.node4"
    //nodelist(4) = "921000006e0012v696.node5"


    val actorList = new Array[ActorRef](4)
    var i = 0;
    for(i <- 0 to 3){
      actorList(i)  = system.actorOf(BinaryAgreementInActor.props(nodelist(i)+"-binaryargree", nodelist(i),"/user","{nodeName}"++"-binaryargree",4,1,probes(i).ref), nodelist(i)+"-binaryargree")
    }

    var msgs : Array[StartBinaryAgreement] = new Array[StartBinaryAgreement](4)
    msgs(0) = new StartBinaryAgreement(nodelist(0),"1",1,1)
    msgs(1) = new StartBinaryAgreement(nodelist(1),"1",1,0)
    msgs(2) = new StartBinaryAgreement(nodelist(2),"1",1,0)
    msgs(3) = new StartBinaryAgreement(nodelist(3),"1",1,0)


    for(i <- 0 to 3){
      probes(i).send(actorList(i),msgs(i))
    }

    for(i <- 0 to 3){
      val recv = probes(i).expectMsgType[ResultOfBA](1000.seconds)
      println(s"actor name=${nodelist(i)}, round:${recv.round},binresult:${recv.binValue}")
    }
  }
}