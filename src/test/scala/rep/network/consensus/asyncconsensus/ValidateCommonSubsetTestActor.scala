package rep.network.consensus.asyncconsensus

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestKit, TestProbe}
import org.json4s.native.Json
import org.json4s.{DefaultFormats, jackson}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType
import rep.network.consensus.asyncconsensus.protocol.validatedagreement.ValidatedAgreement.{StartValidateAgreement, VABA_Result}
import rep.network.consensus.asyncconsensus.protocol.validatedagreement.ValidatedAgreementInActor
import rep.network.consensus.asyncconsensus.protocol.validatedcommonsubset.ValidatedCommonSubetInActor
import rep.network.consensus.asyncconsensus.protocol.validatedcommonsubset.ValidatedCommonSubset.{AddDataToVCS, VCSB_Result}

import scala.concurrent.duration._

class ValidateCommonSubsetTestActor  (_system: ActorSystem)
  extends TestKit(_system) with Matchers with FlatSpecLike with BeforeAndAfterAll {



  def this() = this(ActorSystem("ProvableReliableBroadcastSpec", new ClusterSystem("121000005l35120456.node1", InitType.MULTI_INIT, false).getConf))

  override def afterAll: Unit = {
    shutdown(system)
  }

  implicit val serialization = jackson.Serialization
  // or native.Serialization
  implicit val formats = DefaultFormats

  "ProvableReliableBroadcastSpec" should "assert validate common subset value" in {
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
      actorList(i)  = system.actorOf(ValidatedCommonSubetInActor.props(nodelist(i)+"-vcsb", nodelist(i),"/user","{nodeName}"++"-vcsb",4,1,probes(i).ref), nodelist(i)+"-vcsb")
    }
//AddDataToVCS(leader:String,round:String,data:Array[Byte])
    var msgs : Array[AddDataToVCS] = new Array[AddDataToVCS](4)
    msgs(0) = new AddDataToVCS(nodelist(0),"1"," aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaqaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes("UTF-8"))
    msgs(1) = new AddDataToVCS(nodelist(1),"1","bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".getBytes("UTF-8"))
    msgs(2) = new AddDataToVCS(nodelist(2),"1","ccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc".getBytes("UTF-8"))
    msgs(3) = new AddDataToVCS(nodelist(3),"1","ddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd".getBytes("UTF-8"))


    for(i <- 0 to 3){
      probes(i).send(actorList(i),msgs(i))
    }

    for(i <- 0 to 3){
      val recv = probes(i).expectMsgType[VCSB_Result](1000.seconds)
      println(s"actor name=${nodelist(i)}, round:${recv.round},result:${recv.content.foreach(f=>{
        print(s"name=${f._1},value=${new String(f._2._2)}\t")
      })}")
    }
  }
}