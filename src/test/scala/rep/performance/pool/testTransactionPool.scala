package rep.performance.pool

import java.util.concurrent.{ExecutorService, Executors}

import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
import rep.crypto.cert.SignTool
import rep.network.cache.{TransactionPoolWorker, TransactionPoolWorker1}
import rep.network.tools.transpool.TransactionPoolMgr
import rep.protos.peer.{ChaincodeId, ChaincodeInput, Signature, Transaction}
import rep.storage.ImpDataAccess
import rep.utils.{IdTool, TimeUtils}

import scala.io.Source

object testTransactionPool extends App {
  val transactionmgr = new TransactionPoolMgr
  val nodeNames = new Array[String](5)
  val params = new Array[String](5)

  var chaincode:ChaincodeId = new ChaincodeId("ContractAssetsTPL",1)
  nodeNames(0) = "121000005l35120456.node1"
  nodeNames(1) = "12110107bi45jh675g.node2"
  nodeNames(3) = "122000002n00123567.node3"
  nodeNames(2) = "921000005k36123789.node4"
  nodeNames(4) = "921000006e0012v696.node5"
  protected var works : ExecutorService = Executors.newFixedThreadPool(5)
  protected var testworks : ExecutorService = Executors.newFixedThreadPool(5)

  for(i<-0 to 4){
    getParams(nodeNames(i),i)
  }

  val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(nodeNames(0))

  def getParams(systag:String,idx:Int):Unit={
    val si2 = scala.io.Source.fromFile("api_req/json/transfer_" + systag + ".json","UTF-8")
    params(idx) = try si2.mkString finally si2.close()
  }

  private def authInit(sysTag: String, jksFilePath: String, pwd: String, trustJksFilePath: String, trustPwd: String): Unit = {
    SignTool.loadPrivateKey(sysTag, pwd, jksFilePath)
    SignTool.loadNodeCertList(trustPwd, trustJksFilePath)
  }

  val paths = this.getClass.getResource("").getPath
  val path = paths.substring(0,paths.indexOf("target/scala-2.12/test-classes/rep/performance/pool/"))
  for(i<-0 to 4){
    authInit(nodeNames(i), path+"jks/"+nodeNames(i) + ".jks", "123", path+"jks/mytruststore.jks", "changeme")
  }

  private def createTransForLoop:Array[Transaction]= {
    var count: Int = 20000;
    val trans = new Array[Transaction](count)

    val start = System.currentTimeMillis()
    for(i<-0 to count-1){
      val idx = i % 5
      trans(i) = createTransaction4Invoke(nodeNames(idx), chaincode,
        "transfer", Seq(params(idx)))
      //System.out.print(".")
    }
    val end = System.currentTimeMillis()
    System.out.println(s"Thread:${Thread.currentThread().getId},created spent = ${end - start}ms")
    trans
  }

  private def createTransaction4Invoke(nodeName: String, chaincodeId: ChaincodeId,
                               chaincodeInputFunc: String, params: Seq[String]): Transaction = {
    var t: Transaction = new Transaction()
    val millis = TimeUtils.getCurrentTime()
    if (chaincodeId == null) t

    val txid = IdTool.getRandomUUID
    val cip = new ChaincodeInput(chaincodeInputFunc, params)
    t = t.withId(txid)
    t = t.withCid(chaincodeId)
    t = t.withIpt(cip)
    t = t.withType(rep.protos.peer.Transaction.Type.CHAINCODE_INVOKE)
    t = t.clearSignature
    val certid = IdTool.getCertIdFromName(nodeName)
    var sobj = Signature(Option(certid), Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)))
    sobj = sobj.withSignature(ByteString.copyFrom(SignTool.sign(nodeName, t.toByteArray)))

    t = t.withSignature(sobj)

    t
  }

  /*for(i<-0 to 4)
    this.testworks.execute(new Runnable {
      override def run(): Unit = {
        val trans = createTransForLoop
        System.out.println(s"created transaction , thread:${Thread.currentThread().getId}")
        val start = System.currentTimeMillis()
        trans.foreach(t=>{
          works.execute(new TransactionPoolWorker(transactionmgr,t, dataaccess))
        })
        val end = System.currentTimeMillis()
        System.out.println(s"thread:${Thread.currentThread().getId},add To worker , spent time=${(end - start)}ms")
      }
    })
*/

  for(i<-0 to 4)
    this.testworks.execute(new Runnable {
      override def run(): Unit = {
        val trans = createTransForLoop
        System.out.println(s"created transaction , thread:${Thread.currentThread().getId}")
        val start = System.currentTimeMillis()
        val th = new TransactionPoolWorker1(transactionmgr, dataaccess)
        trans.foreach(t=>{
          th.addTransaction(t)
        })
        works.execute(th)
        val end = System.currentTimeMillis()
        System.out.println(s"thread:${Thread.currentThread().getId},add To worker , spent time=${(end - start)}ms")
      }
    })
}
