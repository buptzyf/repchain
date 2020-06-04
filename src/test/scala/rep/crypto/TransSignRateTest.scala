package rep.crypto

import java.util.concurrent.Executors

import scala.concurrent.duration._
import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
import org.scalatest.time.Second
import rep.crypto.cert.SignTool
import rep.protos.peer.{CertId, ChaincodeId, ChaincodeInput, Signature, Transaction}
import rep.network.PeerHelper
import rep.utils.{IdTool, TimeUtils}

import scala.concurrent._
import scala.concurrent.{ExecutionContext, Future}

object TransSignRateTest extends App {
  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(100))

  SignTool.loadPrivateKey("121000005l35120456.node1", "123", "jks/121000005l35120456.node1.jks")
  SignTool.loadNodeCertList("changeme", "jks/mytruststore.jks")

  val si2 = scala.io.Source.fromFile("api_req/json/transfer_121000005l35120456.node1.json","UTF-8")
  val li2 = try si2.mkString finally si2.close()
  var chaincode:ChaincodeId = new ChaincodeId("ContractAssetsTPL",1)

  val len = 1000
  val isPrintTime = true
  var times = new Array[(Long,Long)](len)

  val listOfFuture: Seq[Future[Boolean]] = (1 to len).map(x => {
    AsyncSignOrVerify(isPrintTime,x)
  })
  val futureOfList = Future.sequence(listOfFuture.toList)
  val results: List[Boolean] = Await.result(futureOfList, 120.seconds)


  /*for(i<-1 to len){
    val t = this.createTrans(isPrintTime,i)
    this.verifyTrans(t,isPrintTime,i)
  }*/


  print

  def print={
    this.times.foreach(f=>{
      System.out.println(s"create trans,spent times=${(f._1)}ms\t verify trans,spent times=${(f._2)}ms")
    })
  }

  def AsyncSignOrVerify(isPrintTime:Boolean,i:Int): Future[Boolean] = Future{
    var result = false
    val v1 = Await.result(asyncCreateTrans(isPrintTime,i),5.seconds)
    if(v1 != null){
      result = Await.result(asyncVerifyTrans(v1,isPrintTime,i),5.seconds)
    }
    result
  }

  def asyncCreateTrans(isPrintTime:Boolean,i:Int): Future[Transaction] = Future {
    var result : Transaction = null
    try{
      result = createTrans(isPrintTime,i)
    }catch{
      case e:Exception => throw e
    }
    result
  } recover { case e: Exception => null }

  def createTrans(isPrintTime:Boolean,i:Int):Transaction={
    val start = System.currentTimeMillis()
    val t3 = PeerHelper.createTransaction4Invoke("121000005l35120456.node1", chaincode,
      "transfer", Seq(li2))
    val end = System.currentTimeMillis()
    times(i-1) = (end-start,0)
    //if(isPrintTime) System.out.println(s"create trans,spent times=${(end-start)}ms")
    t3
  }

  def asyncVerifyTrans(t: Transaction,isPrintTime:Boolean,i:Int): Future[Boolean] = Future {
    var result  = false
    try{
      result = verifyTrans(t,isPrintTime,i)
    }catch{
      case e:Exception => throw e
    }
    result
  } recover { case e: Exception => false }

  def verifyTrans(t: Transaction,isPrintTime:Boolean,i:Int):Boolean={
    val start = System.currentTimeMillis()
    val sig = t.signature.get.signature.toByteArray
    val tOutSig = t.clearSignature
    val certId = t.signature.get.certId.get
    val result = SignTool.verify(sig, tOutSig.toByteArray, certId, "121000005l35120456.node1")
    val end = System.currentTimeMillis()
    val tmp = times(i-1)
    times(i-1) = (tmp._1,end-start)
    //if(isPrintTime) System.out.println(s"verify trans,spent times=${(end-start)}ms")
    result
  }

}
