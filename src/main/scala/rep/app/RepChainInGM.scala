package rep.app

import akka.actor.ActorRef

object RepChainInGM {
  def main(args: Array[String]): Unit = {
    //System.setProperty("javax.net.debug", "all")
    //创建系统实例
    var nodelist : Array[String] = new Array[String] (5)
    nodelist(0) = "215159697776981712.node1"
    nodelist(1) = "904703631549900672.node2"
    nodelist(2) = "989038588418990208.node3"
    nodelist(3) = "645377164372772928.node4"
    nodelist(4) = "379552050023903168.node5"

    for(i <- 0 to 4) {
      Thread.sleep(5000)
      RepChainMgr.Startup4Single(nodelist(i))
    }
    //以下代码只能在测试系统稳定性，即测试系统离网之后再入网时可以用，发布时一定要删除
    //Thread.sleep(10000)
    //RepChainMgr.StartClusterStub
  }
}