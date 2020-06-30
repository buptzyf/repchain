package rep.utils

import akka.util.Timeout

import scala.collection.{Seq, Set}
import scala.concurrent.{Await, Future, TimeoutException}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object testfuture extends App {
  implicit val timeout = Timeout(2.seconds)
  protected def AsyncGetNodeOfChainInfo(flag:Int): Future[String] = Future {
    var result: String = ""


    try {
      if(flag == 5){
        Thread.sleep(5000)
        //throw new Exception("time out")
      }else{
        Thread.sleep(2000)
      }
      result = s"${flag}=ok"
    } catch {

      case te: Exception =>
        println("--------AsyncGetNodeOfChainInfo java timeout")
        result = s"${flag} = timeout"
    }


    result
  }

  protected def AsyncGetNodeOfChainInfos: List[String] = {
    val list = List(1,2,3,4,5)
    val listOfFuture: Seq[Future[String]] = list.toSeq.map(serial => {
      AsyncGetNodeOfChainInfo(serial)
    })

    val futureOfList: Future[List[String]] = Future.sequence(listOfFuture.toList).recover({
      case e: Exception =>
        null
    })

    try {



      val result1 = Await.result(futureOfList, timeout.duration*3).asInstanceOf[List[String]]

      if (result1 == null) {
        List.empty
      } else {
        result1
      }
    } catch {
      case te: TimeoutException =>
        println(s"--------AsyncGetNodeOfChainInfos java timeout")
        null
    }
  }

  val ls = AsyncGetNodeOfChainInfos
  if(ls == null){
    println("result is null")
  }else{
    println(s"result is succuss,${ls.mkString(",")}")
  }

}
