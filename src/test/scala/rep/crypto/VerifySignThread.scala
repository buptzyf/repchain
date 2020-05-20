package rep.crypto

import java.util.concurrent.Executors
import scala.concurrent.duration._
import com.google.protobuf.ByteString
import rep.crypto.cert.SignTool
import rep.protos.peer.CertId

import scala.concurrent.{Await, ExecutionContext, Future}

class VerifySignThread {
  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(100))

  private def asyncVerifySign(srcstr:String,SignStr:ByteString,keyword:CertId,sysname:String): Future[Boolean] = Future {
    var result  = false

    try{
      result = SignTool.verify(SignStr.toByteArray,srcstr.getBytes(),keyword,sysname)
    }catch{
      case e:Exception => throw e
    }
    result
  } recover { case e: Exception => false }



  def VerifySign(srcstrs:Array[String],signinfo:Array[ByteString],certdi:CertId,sysname:String):Array[Boolean]={
    var tmpstruts = new Array[(String,ByteString)](srcstrs.length)
    var i = 0
    srcstrs.foreach(f=>{
      tmpstruts(i) = (f,signinfo(i))
      i += 1
    })
    val start = System.currentTimeMillis()

    val listOfFuture: Seq[Future[Boolean]] = tmpstruts.map(x => {
      asyncVerifySign(x._1,x._2,certdi,sysname)
    })

    val futureOfList = Future.sequence(listOfFuture.toList)

    val results: List[Boolean] = Await.result(futureOfList, 120.seconds)
    val end = System.currentTimeMillis()
    println(s"Verify Sign finish:length=${srcstrs.length},spent times=${(end-start)}ms")
    results.toArray
  }
}
