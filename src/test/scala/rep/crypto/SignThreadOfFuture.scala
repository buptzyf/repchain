package rep.crypto

import java.util.concurrent.{Callable, ExecutorService, Executors, FutureTask, TimeUnit}

import com.google.protobuf.ByteString
import rep.crypto.cert.SignTool
import rep.network.consensus.util.BlockVerify
import rep.protos.peer.{Block, Transaction}
import scala.concurrent.duration._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, ExecutionContext, Future}

class SignThreadOfFuture {
  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(100))
  //implicit val threadPool:ExecutorService=Executors.newFixedThreadPool(1)

  /*def exeSigns(srclist:Array[String],keyword:String):Array[String]= {
    val tasks: ArrayBuffer[FutureTask[String]] = new ArrayBuffer[FutureTask[String]]()

    srclist.map {
      f =>
        val futureTask: FutureTask[String] = new FutureTask(new Callable[String] {
          def call: String = {

            "any"
          }
        });
        tasks+=(futureTask)
        threadPool.execute(futureTask)
    }


    val lres = new java.util.ArrayList[String]()
    tasks.map {
      f =>
        val task: FutureTask[String] = f
        try {
          val sinfo = task.get(30, TimeUnit.MILLISECONDS)
          lres.add(sinfo)
        } catch {
          case e: Exception => {
            println("异步请求出错：" + e.printStackTrace)
            task.cancel(true)
          }
        }
    }

    var result = new Array[String](srclist.length)
    lres.toArray(result)
  }
*/

  private def asyncSign(srcstr:String,keyword:String): Future[ByteString] = Future {
    var result : ByteString = ByteString.EMPTY

    try{
      result = ByteString.copyFrom(SignTool.sign(keyword,srcstr.getBytes()))
    }catch{
      case e:Exception => throw e
    }
    result
  } recover { case e: Exception => ByteString.EMPTY }

  def sign(srcstrs:Array[String],keyword:String):Array[ByteString]={
    val start = System.currentTimeMillis()
    val listOfFuture: Seq[Future[ByteString]] = srcstrs.map(x => {
      asyncSign(x,keyword)
    })

    val futureOfList = Future.sequence(listOfFuture.toList)

    val results: List[ByteString] = Await.result(futureOfList, 120.seconds)
    val end = System.currentTimeMillis()
    println(s"sign finish:length=${srcstrs.length},spent times=${(end-start)}ms")
    results.toArray
  }


}
