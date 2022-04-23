package rep.storage.test

import java.util.Date

import rep.app.conf.RepChainConfig
import rep.storage.db.common.{IDBAccess, ITransactionCallback}
import rep.storage.db.factory.DBFactory

import scala.util.control.Breaks.{break, breakable}
import scala.collection.immutable.HashMap
import scala.util.Random

object TestOfDBAccess extends App {
  val systemName = "215159697776981712.node1"
  val config = new RepChainConfig(systemName)
  var testCount = 0
  var errorCount = 0
  var rightCount = 0

  test

  private def generateBigData(num: Int, isUseDefault: Boolean) = {
    var str = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    if (!isUseDefault) {
      val random = new Random
      val sb = new StringBuffer
      for (i <- 0 until num) {
        val number = random.nextInt(62)
        sb.append(str.charAt(number))
      }
      str = sb.toString
    }
    str
  }

  def getValue:Any={
    val data = Random.nextInt(1000) % 5 match {
      case 0 =>
        generateBigData(50, false)
      case 1 =>
        Random.nextInt(10000)
      case 2 =>
        Random.nextLong()
      case 3 =>
        new Date()
      case 4 =>
        generateBigData(300, false).getBytes()
    }
    data
  }

  def getKey:String={
    generateBigData(30, false)
  }

  def dataCheck(d:Any,previousValue:Any):Unit={
    testCount += 1
    d match {
      case d:Int =>
        if(d.asInstanceOf[Int]==previousValue.asInstanceOf[Int]) rightCount += 1 else errorCount += 1
        System.out.println(s"data type is (Int),data=${d},previous=${previousValue}," +
          s"is equal:${d.asInstanceOf[Int]==previousValue.asInstanceOf[Int]}")
      case d:Long =>
        if(d.asInstanceOf[Long]==previousValue.asInstanceOf[Long]) rightCount += 1 else errorCount += 1
        System.out.println(s"data type is (Long),data=${d},previous=${previousValue}," +
          s"is equal:${d.asInstanceOf[Long]==previousValue.asInstanceOf[Long]}")
      case d:String=>
        if(d.asInstanceOf[String]==previousValue.asInstanceOf[String]) rightCount += 1 else errorCount += 1
        System.out.println(s"data type is (String),data=${d},previous=${previousValue}," +
          s"is equal:${d.asInstanceOf[String]==previousValue.asInstanceOf[String]}")
      case d:Date=>
        if(d.asInstanceOf[Date].getTime==previousValue.asInstanceOf[Date].getTime) rightCount += 1 else errorCount += 1
        System.out.println(s"data type is (Date),data=${d.toString},previous=${previousValue.toString}," +
          s"is equal:${d.asInstanceOf[Date].getTime==previousValue.asInstanceOf[Date].getTime}")
      case d:Array[Byte]=>
        if(new String(d.asInstanceOf[Array[Byte]]).toString==new String(previousValue.asInstanceOf[Array[Byte]]).toString) rightCount += 1 else errorCount += 1
        System.out.println(s"data type is (Array[Byte]),data=${new String(d.asInstanceOf[Array[Byte]]).toString},previous=${new String(previousValue.asInstanceOf[Array[Byte]]).toString}," +
          s"is equal:${new String(d.asInstanceOf[Array[Byte]]).toString==new String(previousValue.asInstanceOf[Array[Byte]]).toString}")
    }
  }

  def dataCheck4Value(key:String,previousValue:Any,db:IDBAccess):Unit={
    testCount += 1
    previousValue match {
      case d:Int =>
        val r = db.getObject[Int](key).get
        if(r.asInstanceOf[Int]==previousValue.asInstanceOf[Int]) rightCount += 1 else errorCount += 1
        System.out.println(s"data type is (Int),data=${r},previous=${previousValue}," +
          s"is equal:${r.asInstanceOf[Int]==previousValue.asInstanceOf[Int]}")
      case d:Long =>
        val r = db.getObject[Long](key).get
        if(r.asInstanceOf[Long]==previousValue.asInstanceOf[Long]) rightCount += 1 else errorCount += 1
        System.out.println(s"data type is (Long),data=${r},previous=${previousValue}," +
          s"is equal:${r.asInstanceOf[Long]==previousValue.asInstanceOf[Long]}")
      case d:String=>
        val r = db.getObject[String](key).get
        if(r.asInstanceOf[String]==previousValue.asInstanceOf[String]) rightCount += 1 else errorCount += 1
        System.out.println(s"data type is (String),data=${r},previous=${previousValue}," +
          s"is equal:${r.asInstanceOf[String]==previousValue.asInstanceOf[String]}")
      case d:Date=>
        val r = db.getObject[Date](key).get
        if(r.asInstanceOf[Date].getTime==previousValue.asInstanceOf[Date].getTime) rightCount += 1 else errorCount += 1
        System.out.println(s"data type is (Date),data=${r.toString},previous=${previousValue.toString}," +
          s"is equal:${r.asInstanceOf[Date].getTime==previousValue.asInstanceOf[Date].getTime}")
      case d:Array[Byte]=>
        val r = db.getObject[Array[Byte]](key).get
        if(new String(r.asInstanceOf[Array[Byte]]).toString==new String(previousValue.asInstanceOf[Array[Byte]]).toString) rightCount += 1 else errorCount += 1
        System.out.println(s"data type is (Array[Byte]),data=${new String(r.asInstanceOf[Array[Byte]]).toString},previous=${new String(previousValue.asInstanceOf[Array[Byte]]).toString}," +
          s"is equal:${new String(r.asInstanceOf[Array[Byte]]).toString==new String(previousValue.asInstanceOf[Array[Byte]]).toString}")
    }
  }

  def test:Unit={
    var hm : HashMap[String,Any] = new HashMap[String,Any]()
    for(i<-0 to 100){
      val k = getKey
      val v = getValue
      hm += k -> v
    }

    val db = DBFactory.getDBAccess(config)
    //test put object


    hm.foreach(f=>{
      val k = f._1
      val v = f._2
      db.putObject(k,v)
    })

    //test get object
    hm.foreach(f=>{
      val k = f._1
      val v = f._2
      val d = db.getObject(k)
      if(d != None){
        dataCheck(d.get,v)
      }
    })

    var gk = ""
    hm.foreach(f=>{
      val k = f._1
      val v = f._2
      gk = k
      dataCheck4Value(k,v,db)
    })

    val tv = db.getObject(gk)

    db.delete(gk)
    val v = db.getObject(gk)
    testCount += 1
    if(v == None){
      rightCount += 1
    }else{
      errorCount += 1
    }

    transactionTest(true,db)
    transactionTest(false,db)
    printTestResult
  }

  def transactionTest(isRollback:Boolean,db:IDBAccess):Unit={
    var hm1 : HashMap[String,Any] = new HashMap[String,Any]()
    var ks = new Array[String](10)
    for(i<-0 to 9){
      val k = getKey
      val v = getValue
      hm1 += k -> v
      ks(i) = k
    }

    db.transactionOperate(new ITransactionCallback {
      override def callback: Boolean = {
        var r = true
        breakable(
          for(i<-0 to 9){
            val k = ks(i)
            val v = hm1(k)
            db.putObject(k,v)
            if(i == 5 && isRollback){
              r = false
              break
            }
          }
        )
        r
      }
    }
    )

    if(isRollback){
      ks.foreach(k=>{
        testCount += 1
        val d = db.getObject(k)
        if(d == None){
          System.out.println("----rollback---right---key not exist")
          rightCount += 1
        }else{
          errorCount += 1
        }
      })
    }else{
      ks.foreach(k=>{
        testCount += 1
        val d = db.getObject(k)
        if(d != None){
          rightCount += 1
          System.out.println("----commit---right---key exist")
        }else{
          errorCount += 1
        }
      })
    }
  }

  def printTestResult:Unit={
    System.out.println(s"test result:testCount=${testCount},errorCount=${errorCount},rightCount=${rightCount}")
  }
}
