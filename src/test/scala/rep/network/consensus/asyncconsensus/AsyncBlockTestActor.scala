package rep.network.consensus.asyncconsensus

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestKit, TestProbe}
import org.json4s.native.Serialization.{write, writePretty}
import org.json4s.{DefaultFormats, jackson}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import rep.app.conf.SystemProfile
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType
import rep.network.autotransaction.PeerHelper
import rep.network.consensus.asyncconsensus.AsyncBlockTestActor.{ACTION, CertInfo}
import rep.network.consensus.asyncconsensus.protocol.block.AsyncBlock.{StartBlock, TransactionsOfBlock}
import rep.network.consensus.asyncconsensus.protocol.block.AsyncBlockInActor
import rep.network.module.cfrd.ModuleManagerOfCFRD
import rep.protos.peer.{Certificate, ChaincodeId, Signer}

import scala.concurrent.duration._
import scala.collection.mutable
import scala.io.BufferedSource

object AsyncBlockTestActor{
  object ACTION {
    val transfer = "transfer"
    val set = "set"
    val SignUpSigner = "SignUpSigner"
    val SignUpCert = "SignUpCert"
    val UpdateCertStatus = "UpdateCertStatus"
    val UpdateSigner = "UpdateSigner"
  }

  final case class CertStatus(credit_code: String, name: String, status: Boolean)
  final case class CertInfo(credit_code: String, name: String, cert: Certificate)
}

class AsyncBlockTestActor(_system: ActorSystem)
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
    val dbTag = "121000005l35120456.node1"
    val cid = ChaincodeId(SystemProfile.getAccountChaincodeName, 1)
    val pm: ActorRef = system.actorOf(ModuleManagerOfCFRD.props("modulemanager", sysName, false, false, false), "modulemanager")


    val signers: Array[Signer] = Array(
      Signer("node1", "121000005l35120456", "18912345678", List("node1")),
      Signer("node2", "12110107bi45jh675g", "18912345678", List("node2")),
      Signer("node3", "122000002n00123567", "18912345678", List("node3", "zyf")),
      Signer("node4", "921000005k36123789", "18912345678", List("node4", "c4w")),
      Signer("node5", "921000006e0012v696", "18912345678", List("node5")),
      Signer("super_admin", "951002007l78123233", "18912345678", List("super_admin"))
    )

    val certNode1: BufferedSource = scala.io.Source.fromFile("jks/certs/121000005l35120456.node1.cer")
    val certStr1: String = try certNode1.mkString finally certNode1.close()
    val certNode2: BufferedSource = scala.io.Source.fromFile("jks/certs/12110107bi45jh675g.node2.cer")
    val certStr2: String = try certNode2.mkString finally certNode2.close()
    val certNode3: BufferedSource = scala.io.Source.fromFile("jks/certs/122000002n00123567.node3.cer")
    val certStr3: String = try certNode3.mkString finally certNode3.close()
    val certNode4: BufferedSource = scala.io.Source.fromFile("jks/certs/921000005k36123789.node4.cer")
    val certStr4: String = try certNode4.mkString finally certNode4.close()
    val certNode5: BufferedSource = scala.io.Source.fromFile("jks/certs/921000006e0012v696.node5.cer")
    val certStr5: String = try certNode5.mkString finally certNode5.close()
    val certs: mutable.Map[String, String] = mutable.Map("node1" -> certStr1, "node2" -> certStr2, "node3" -> certStr3, "node4" -> certStr4, "node5" -> certStr5)

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
      actorList(i)  = system.actorOf(AsyncBlockInActor.props(nodelist(i)+"-asyncblock", nodelist(i),"/user","{nodeName}"++"-asyncblock",4,1,probes(i).ref), nodelist(i)+"-asyncblock")
    }



    //(round:String,leader:String,transList:Seq[Transaction])
    var msgs : Array[StartBlock] = new Array[StartBlock](4)
    for(i<-0 to 3){
      val certInfo = CertInfo("121000005l35120456", "node1", Certificate(certs.getOrElse("node1", "default"), "SHA256withECDSA", certValid = true, None, None))
      val t = PeerHelper.createTransaction4Invoke(sysName, cid, ACTION.SignUpCert, Seq(writePretty(certInfo)))
      msgs(i) = new StartBlock("1",nodelist(i),Seq(t))
    }

    for(i <- 0 to 3){
      probes(i).send(actorList(i),msgs(i))
    }

    for(i <- 0 to 3){
      val recv = probes(i).expectMsgType[TransactionsOfBlock](1000.seconds)
      println(s"actor name=${nodelist(i)}, round:${recv.round},result:${recv.transList.foreach(f=>{
        print(s"name=${f.id},value=${f.toByteString}\t")
      })}")
    }
  }
}
