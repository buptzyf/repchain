package rep.crypto

import rep.crypto.cert.SignTool
import rep.crypto.SignThreadOfFuture
import rep.crypto.VerifySignThread
import rep.protos.peer.CertId

object SignRateTest extends App {

  SignTool.loadPrivateKey("121000005l35120456.node1", "123", "jks/121000005l35120456.node1.jks")
  SignTool.loadNodeCertList("changeme", "jks/mytruststore.jks")

  val len = 50000
  val srclist = getStringList(len)

  var signthread = new SignThreadOfFuture()
  var signresult =signthread.sign(srclist,"121000005l35120456.node1")

  val certid = new CertId("121000005l35120456","node1")

  var verifysign = new VerifySignThread()
  var verresult = verifysign.VerifySign(srclist,signresult,certid,"121000005l35120456.node1")
  /*signresult.foreach(f=>{
    println(f)
  })*/

  /*verresult.foreach(f=>{
    println(f)
  })*/

  def getStringList(listlen:Int):Array[String] = {
    var result = new Array[String](listlen)
    var loop = 0
    for(loop <- 1 to listlen){
      result(loop-1) = getRandomString
    }
    result
  }

  def getRandomString:String={
    var loop = 0
    var result : StringBuffer = new StringBuffer()
    for(loop <- 1 to 10){
      result.append(scala.util.Random.nextLong().toString).append("_")
    }
    result.append("0")
    result.toString
  }

}
