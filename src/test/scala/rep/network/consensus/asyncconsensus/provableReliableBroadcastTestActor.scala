package rep.network.consensus.asyncconsensus

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestKit, TestProbe}
import com.google.protobuf.ByteString
import org.json4s.{DefaultFormats, jackson}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType
import rep.network.consensus.asyncconsensus.protocol.commoncoin.CommonCoinInActor
import rep.network.consensus.asyncconsensus.protocol.provablereliablebroadcast.ProvableReliableBroadcastInActor
import rep.protos.peer.{ResultOfCommonCoin, ResultOfPRBC, StartCommonCoin, StartOfPRBC}

import scala.concurrent.duration._

class provableReliableBroadcastTestActor (_system: ActorSystem)
  extends TestKit(_system) with Matchers with FlatSpecLike with BeforeAndAfterAll {

  def this() = this(ActorSystem("ProvableReliableBroadcastSpec", new ClusterSystem("121000005l35120456.node1", InitType.MULTI_INIT, false).getConf))

  override def afterAll: Unit = {
    shutdown(system)
  }

  implicit val serialization = jackson.Serialization
  // or native.Serialization
  implicit val formats = DefaultFormats

  "ProvableReliableBroadcastSpec" should "assert provableReliableBroadcast value" in {
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

    var probes = new Array[TestProbe](4)
    for(i <- 0 to 3){
      probes(i) = TestProbe()
    }

    val actorList = new Array[Array[ActorRef]](4)
    var i = 0
    var j = 0
    for(i <- 0 to 3){
      var actorSonList = new Array[ActorRef](4)
      for(j <- 0 to 3){
        actorSonList(j)  = system.actorOf(ProvableReliableBroadcastInActor.props(nodelist(i)+"-"+nodelist(j)+"_PRBC",nodelist(i),"/user","{nodeName}-"+nodelist(j)+"_PRBC", 4,1,probes(i).ref), nodelist(i)+"-"+nodelist(j)+"_PRBC")
      }
      actorList(i) = actorSonList
    }

    var msg = new Array[StartOfPRBC](4)
    msg(0) = new StartOfPRBC(nodelist(0),"2",ByteString.copyFromUtf8("my transactions bytes---aaa"))
    msg(1) = new StartOfPRBC(nodelist(1),"2",ByteString.copyFromUtf8("my transactions bytes---bbb"))
    msg(2) = new StartOfPRBC(nodelist(2),"2",ByteString.copyFromUtf8("my transactions bytes---ccc"))
    msg(3) = new StartOfPRBC(nodelist(3),"2",ByteString.copyFromUtf8("my transactions bytes---ddd"))

    for(i<-0 to 3){
      probes(i).send(actorList(i)(i),msg(i))
    }

    for(i<-0 to 3){
      val recv = probes(i).expectMsgType[ResultOfPRBC](1000.seconds)
      println(s"actor serial:${i},expect source:hello,nodename:${recv.sendName},moduleName:${recv.moduleName},round:${recv.round},roothash:${recv.rootHash.toStringUtf8}" +
        s",data:${recv.transationsCipher.toStringUtf8}")
    }



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