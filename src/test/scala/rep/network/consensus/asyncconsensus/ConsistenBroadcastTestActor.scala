package rep.network.consensus.asyncconsensus

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestKit, TestProbe}
import com.google.protobuf.ByteString
import org.json4s.{DefaultFormats, jackson}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType
import rep.network.consensus.asyncconsensus.protocol.consistentbroadcast.ConsistentBroadcast.{CBC_RESULT, StartConsistenBroadcast}
import rep.network.consensus.asyncconsensus.protocol.consistentbroadcast.ConsistentBroadcastInActor

import scala.concurrent.duration._

class ConsistenBroadcastTestActor  (_system: ActorSystem)
  extends TestKit(_system) with Matchers with FlatSpecLike with BeforeAndAfterAll {

  def this() = this(ActorSystem("ProvableReliableBroadcastSpec", new ClusterSystem("121000005l35120456.node1", InitType.MULTI_INIT, false).getConf))

  override def afterAll: Unit = {
    shutdown(system)
  }

  implicit val serialization = jackson.Serialization
  // or native.Serialization
  implicit val formats = DefaultFormats

  "ProvableReliableBroadcastSpec" should "assert consistenBroadcast value" in {
    val sysName = "121000005l35120456.node1"

    var nodelist : Array[String] = new Array[String] (4)
    nodelist(0) = "121000005l35120456.node1"
    nodelist(1) = "12110107bi45jh675g.node2"
    nodelist(2) = "122000002n00123567.node3"
    nodelist(3) = "921000005k36123789.node4"
    //nodelist(4) = "921000006e0012v696.node5"

    /*var probes = new Array[TestProbe](4)
    for(i <- 0 to 3){
      probes(i) = TestProbe()
    }*/

    val probe = TestProbe()

    val actorList = new Array[ActorRef](4)
    var i = 0;
    for(i <- 0 to 3){
      actorList(i)  = system.actorOf(ConsistentBroadcastInActor.props(nodelist(i)+"-"+nodelist(0)+"_CBC",nodelist(i),"/user","{nodeName}-"+nodelist(0)+"_CBC", 4,1,probe.ref), nodelist(i)+"-"+nodelist(0)+"_CBC")
    }


    val msg = new StartConsistenBroadcast("cbc","3",nodelist(0),nodelist(0)+"_CBC","my transactions bytes")

    probe.send(actorList(0),msg)
    val recv = probe.expectMsgType[CBC_RESULT](1000.seconds)

    println(s"actor serial:expect source:my transactions bytes,round:${recv.round}," +
      s"nodename:${recv.leader},cmbsign:${recv.cmbSign}" )


    /*for(i <- 0 to 3){
      probes(i).send(actorList(i),msg)
    }*/

    /*for(i <- 0 to 3){
      val recv = probes(i).expectMsgType[ResultOfCommonCoin](1000.seconds)
      if(recv.source == "hello"){
        println(s"actor serial:${i},expect source:hello,exeresult:${recv.result}")
      }
    }*/
  }
}