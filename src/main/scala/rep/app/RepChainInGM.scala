package rep.app

import akka.actor.ActorRef

object RepChainInGM {

  def h4(h:String) = {
    if (h.size >= 4)
      h.substring(0,4)
    else
      h
  }

  def nn(s:String) = {
    var r = ""
    if (s.contains("215159697776981712.node1")) r = "node1"
    if (s.contains("904703631549900672.node2")) r = "node2"
    if (s.contains("989038588418990208.node3")) r = "node3"
    if (s.contains("645377164372772928.node4")) r = "node4"
    if (s.contains("379552050023903168.node5")) r = "node5"
    r
  }

  def nn(sender:ActorRef) = {
    var r = ""
    val s = sender.path.toString
    if (s.contains("22522")) r = "node1"
    if (s.contains("22523")) r = "node2"
    if (s.contains("22524")) r = "node3"
    if (s.contains("22525")) r = "node4"
    if (s.contains("22526")) r = "node5"
    r
  }

  def main(args: Array[String]): Unit = {
    //System.setProperty("javax.net.debug", "all")
    //创建系统实例
    var nodelist : Array[String] = new Array[String] (5)
    nodelist(0) = "215159697776981712.node1"
    nodelist(1) = "904703631549900672.node2"
    nodelist(2) = "989038588418990208.node3"
    nodelist(3) = "645377164372772928.node4"
    nodelist(4) = "379552050023903168.node5"
    var nodeports : Array[Int] = new Array[Int](5)
    nodeports(0) = 22522
    nodeports(1) = 22523
    nodeports(2) = 22524
    nodeports(3) = 22525
    nodeports(4) = 22526

    var nodehports : Array[Int] = new Array[Int](5)
    nodehports(0) = 9081
    nodehports(1) = 9082
    nodehports(2) = 9083
    nodehports(3) = 9084
    nodehports(4) = 9085

    for(i <- 0 to 4) {
      Thread.sleep(5000)
      RepChainMgr.Startup4Multi(nodelist(i),nodeports(i),nodehports(i))
    }
    //以下代码只能在测试系统稳定性，即测试系统离网之后再入网时可以用，发布时一定要删除
    //Thread.sleep(10000)
    //RepChainMgr.StartClusterStub
  }
}