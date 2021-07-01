package rep.network.consensus.asyncconsensus

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestKit, TestProbe}
import org.json4s.{DefaultFormats, jackson}
import scala.concurrent.duration._
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType
import rep.network.consensus.asyncconsensus.protocol.commoncoin.CommonCoinInActor
import rep.protos.peer.{ResultOfCommonCoin, StartCommonCoin}

class CommonCoinTestActor(_system: ActorSystem)
  extends TestKit(_system) with Matchers with FlatSpecLike with BeforeAndAfterAll {



  def this() = this(ActorSystem("ProvableReliableBroadcastSpec", new ClusterSystem("121000005l35120456.node1", InitType.MULTI_INIT, false).getConf))

  override def afterAll: Unit = {
    shutdown(system)
  }

  implicit val serialization = jackson.Serialization
  // or native.Serialization
  implicit val formats = DefaultFormats

  "ProvableReliableBroadcastSpec" should "assert get coin value" in {
    val sysName = "121000005l35120456.node1"
    //val dbTag = "121000005l35120456.node1"
    //val pm = system.actorOf(ModuleManagerOfDUMBO.props("modulemanager", sysName, false, false, false), "modulemanager")

    var nodelist : Array[String] = new Array[String] (4)
    nodelist(0) = "121000005l35120456.node1"
    nodelist(1) = "12110107bi45jh675g.node2"
    nodelist(2) = "122000002n00123567.node3"
    nodelist(3) = "921000005k36123789.node4"
    //nodelist(4) = "921000006e0012v696.node5"

    var probes = new Array[TestProbe](4)
    for(i <- 0 to 3){
      probes(i) = TestProbe()
    }

    val actorList = new Array[ActorRef](4)
    var i = 0;
    for(i <- 0 to 3){
      actorList(i)  = system.actorOf(CommonCoinInActor.props(nodelist(i)+"-CommonCoin", nodelist(i),"/user","{nodeName}"+"-CommonCoin",true,probes(i).testActor), nodelist(i)+"-CommonCoin")
    }


    val msg = new StartCommonCoin("hello",true,"2")
    for(i <- 0 to 3){
      probes(i).send(actorList(i),msg)
    }

    for(i <- 0 to 3){
      val recv = probes(i).expectMsgType[ResultOfCommonCoin](1000.seconds)
      if(recv.source == "hello"){
        println(s"actor serial:${i},expect source:hello,exeresult:${recv.result}")
      }
    }
  }
}