package rep.storage.test

import rep.storage.ImpDataAccess
import java.util.concurrent.Executors

import rep.log.RepTimeTracer
import rep.protos.peer.Block

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext
import scala.util.Random

class testRateOfLevelDB(da:ImpDataAccess) {
  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(100))

  private def readKey(key:String): Future[Array[Byte]] = Future {
    var result  : Array[Byte]= null

    try{
      result = da.Get(key)
    }catch{
      case e:Exception => throw e
    }
    result
  } recover { case e: Exception => null }

  private def writeKey(key:String): Future[Boolean] = Future {
    var result = true

    val pre = "writekey statksksks-"
    try{
      val v = (pre+Random.nextInt().toString).getBytes()
      da.Put(key+"_1",v)
      da.Put(key+"_2",v)
    }catch{
      case e:Exception => throw e
    }
    result
  } recover { case e: Exception => false }

  private def readkeyInFuture(keys:Array[String]):Future[Boolean] = Future{
    var result = true
    var i = 0
    val start = System.currentTimeMillis()
    val listOfFuture: Seq[Future[Array[Byte]]] = keys.map(x => {
      readKey(x)
    })
    val futureOfList : Future[List[Array[Byte]]]= Future.sequence(listOfFuture.toList)

    futureOfList.map(x => {
      x.foreach(f => {
        if (f == null) {
          result = false
        }
      })
      })
    val end = System.currentTimeMillis()
    println(s"read  finish:length=${keys.length},spent times=${(end-start)}ms")
    true
  }

  private def writekeyInFuture(keys:Array[String]):Future[Boolean] = Future{
    var result = true
    var i = 0
    val start = System.currentTimeMillis()
    val listOfFuture: Seq[Future[Boolean]] = keys.map(x => {
      writeKey(x)
    })
    val futureOfList : Future[List[Boolean]]= Future.sequence(listOfFuture.toList)

    futureOfList.map(x => {
      x.foreach(f => {
        if (!f) {
          result = false
        }
      })

    })
    val end = System.currentTimeMillis()
    println(s"write finish:length=${keys.length},spent times=${(end-start)}ms")
    true
  }

   def readAndWrite(keys:Array[String]) = {
    val start = System.currentTimeMillis()
    val r = readkeyInFuture(keys)
    val w = writekeyInFuture(keys)

    val result = for {
      v1 <- r
      v2 <- w

    } yield (v1 && v2)

    val result1 = Await.result(result, 120.seconds*2).asInstanceOf[Boolean]

    val end = System.currentTimeMillis()
    println(s"read and write finish:length=${keys.length},spent times=${(end-start)}ms")
    result1
  }
}