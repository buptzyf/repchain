package rep.network.consensus.asyncconsensus

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestKit, TestProbe}
import org.json4s.{DefaultFormats, jackson}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType
import rep.network.consensus.asyncconsensus.protocol.binaryagreement.BinaryAgreement.{ResultOfBA, StartBinaryAgreement}
import rep.network.consensus.asyncconsensus.protocol.binaryagreement.BinaryAgreementInActor
import rep.network.consensus.asyncconsensus.protocol.validatedagreement.ValidatedAgreement.{StartValidateAgreement, VABA_Result}
import rep.network.consensus.asyncconsensus.protocol.validatedagreement.ValidatedAgreementInActor

import scala.concurrent.duration._

class ValidateAgreementTestActor (_system: ActorSystem)
  extends TestKit(_system) with Matchers with FlatSpecLike with BeforeAndAfterAll {



  def this() = this(ActorSystem("ProvableReliableBroadcastSpec", new ClusterSystem("121000005l35120456.node1", InitType.MULTI_INIT, false).getConf))

  override def afterAll: Unit = {
    shutdown(system)
  }

  implicit val serialization = jackson.Serialization
  // or native.Serialization
  implicit val formats = DefaultFormats

  "ProvableReliableBroadcastSpec" should "assert validate agreement value" in {
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
      actorList(i)  = system.actorOf(ValidatedAgreementInActor.props(nodelist(i)+"-vaba", nodelist(i),"/user","{nodeName}"++"-vaba",4,1,probes(i).ref), nodelist(i)+"-vaba")
    }

    var msgs : Array[StartValidateAgreement] = new Array[StartValidateAgreement](4)
    msgs(0) = new StartValidateAgreement("1",nodelist(0),"my sign0")
    msgs(1) = new StartValidateAgreement("1",nodelist(1),"my sign1")
    msgs(2) = new StartValidateAgreement("1",nodelist(2),"my sign2")
    msgs(3) = new StartValidateAgreement("1",nodelist(3),"my sign3")


    for(i <- 0 to 3){
      probes(i).send(actorList(i),msgs(i))
    }

    for(i <- 0 to 3){
      val recv = probes(i).expectMsgType[VABA_Result](1000.seconds)
      println(s"actor name=${nodelist(i)}, round:${recv.round},sendName:${recv.sendName},result=${recv.result.toString}")
    }
  }
}