package rep.accumulator


import rep.accumulator.CreateTestTransactionService.tx_data
import rep.app.system.RepChainSystemContext
import rep.crypto.Sha256
import rep.proto.rc2.{ChaincodeId, Transaction}
import rep.storage.db.factory.DBFactory
import rep.utils.{IdTool, SerializeUtils}
import java.math.BigInteger
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

object CreateTestTransactionService{
  case class tx_data(tx:Array[Byte],prime:BigInteger)

  def main(args: Array[String]): Unit = {
    val s = new CreateTestTransactionService
    val td = s.readTx
    if (td != null) {
      System.out.println(s"read tx,prime=${td.prime}")
    } else {
      System.out.println("not read data")
    }
    val td1 = s.readTx
    if (td1 != null) {
      System.out.println(s"read tx,prime=${td1.prime}")
    } else {
      System.out.println("not read data")
    }
  }

}


/**
 * 为累加器的测试生成交易，并计算交易的大素数
 * */
class CreateTestTransactionService {
  val test_transaction_prefix = "acc_test_tx_"
  val test_max_key = "acc_test_tx_max"
  val max_tx_limited: Int = 10000000
  var current_max_tx_number: AtomicInteger = new AtomicInteger(-1)
  var read_tx_number : AtomicInteger = new AtomicInteger(0)
  val ctx = new RepChainSystemContext("121000005l35120456.node1")
  val keyFileSuffix = ctx.getCryptoMgr.getKeyFileSuffix
  ctx.getSignTool.loadPrivateKey("121000005l35120456.node1", "123", s"${keyFileSuffix.substring(1)}/${ctx.getConfig.getChainNetworkId}/" + "121000005l35120456.node1" + s"${keyFileSuffix}")
  val chaincode = new ChaincodeId("ContractAssetsTPL",1)
  val transactionContent = getTransactionContent

  val queue:ConcurrentLinkedQueue[tx_data] = new ConcurrentLinkedQueue[tx_data]()
  val th_number = 10


  val db = DBFactory.getDBAccess(ctx.getConfig)
  checkTransactionContent

  def getContext:RepChainSystemContext={
    this.ctx
  }

  private def checkTransactionContent:Unit={
    val v = db.getObject[Int](this.test_max_key)
    if (v != None) {
      System.out.println(s"init get transaction，current transactions:${v.get}")
      val tmp = if(v.get.isInstanceOf[Int]) v.get.asInstanceOf[Int] else -1
      this.current_max_tx_number.set(tmp)
    }
    if(this.current_max_tx_number.get() < this.max_tx_limited){
      val thread = new Thread(new createTransactionThread)
      thread.start()
    }
  }

  def resetReadNumber:Unit={
    this.read_tx_number.set(0)
  }

  def setReadNumber(no:Int):Unit={
    this.read_tx_number.set(no)
  }

  def readTx:tx_data={
    var r : tx_data = null
    if(read_tx_number.get() > current_max_tx_number.get()){
      Thread.sleep(5000)
    }
    if(read_tx_number.get() < current_max_tx_number.get()){
      val v = db.getObject[tx_data](test_transaction_prefix + read_tx_number.getAndAdd(1))
      if (v != None) {
        r = v.get
      }
    }
    r
  }

  class createThread(count:Int) extends Runnable{
    override def run(): Unit = {
      try {
        val start1 = System.currentTimeMillis()
        val ht = new Sha256(ctx.getCryptoMgr.getInstance)
        for(i<-0 to count){
          val t = getTransaction
          val tb = t.toByteArray
          val prime = PrimeTool.hash2Prime(tb, Accumulator.bitLength, ht)
          val tc = tx_data(tb, prime)
          queue.add(tc)
        }
        System.out.println(s"createThread${Thread.currentThread().getId} finish,time=${(System.currentTimeMillis() - start1) / 1000}s")
      } catch {
        case e: Exception =>
          e.printStackTrace()
          Thread.sleep(2000)
      }
    }
  }

  class createTransactionThread extends Runnable{

    val num = max_tx_limited - current_max_tx_number.get()
    if(num > 0){
      val c = num / th_number
      for(i<-0 to th_number-1){
        val th = new Thread(new createThread(c+1000))
        th.start()
      }
    }

    override def run(): Unit = {
      try{
        var start = System.currentTimeMillis()
        val start1 = System.currentTimeMillis()
        while (current_max_tx_number.get() < max_tx_limited) {
          /*val t = getTransaction
          val tb = t.toByteArray
          val prime = PrimeTool.hash2Prime(tb,Accumulator.bitLength,ctx.getHashTool)
          val tc = tx_data(tb,prime)*/
          val tc = queue.poll()
          if(tc != null){
            db.putBytes(test_transaction_prefix + current_max_tx_number.addAndGet(1), SerializeUtils.serialise(tc))
            db.putBytes(test_max_key, SerializeUtils.serialise(current_max_tx_number.get()))
            if (current_max_tx_number.get() % 1000 == 0) {
              System.out.println(s"current transaction total=${current_max_tx_number.get()},time=${(System.currentTimeMillis() - start) / 1000}s")
              start = System.currentTimeMillis()
            }
          }
        }
        System.out.println(s"finish,current transaction total=${current_max_tx_number.get()},time=${(System.currentTimeMillis()-start1)/1000}s")
      }catch {
        case e:Exception=>
          e.printStackTrace()
          Thread.sleep(2000)
      }
    }
  }

  private def getTransaction: Transaction = {
    ctx.getTransactionBuilder.createTransaction4Invoke(ctx.getConfig.getChainNetworkId + IdTool.DIDPrefixSeparator + ctx.getSystemName,
      chaincode, "transfer", Seq(transactionContent))
  }

  private def getTransactionContent: String = {
    var fpath = "api_req/json/transfer_" + ctx.getSystemName + ".json"
    if (ctx.getConfig.isUseGM) {
      fpath = "api_req/json/gm/transfer_" + ctx.getSystemName + ".json"
    }
    val si2 = scala.io.Source.fromFile(fpath, "UTF-8")
    try si2.mkString finally si2.close()
  }


}
