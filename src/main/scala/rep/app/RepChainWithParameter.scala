package rep.app

object RepChainWithParameter {
  def main(args: Array[String]): Unit = {
    if(args.length >= 3){
      val nodelist = args(0).split(",")
      if(nodelist.length >= 4){
        try{
          var nodeport = Integer.parseInt(args(1).toString)
          var nodehport = Integer.parseInt(args(2).toString)
          for(i <- 0 to nodelist.length) {
            Thread.sleep(5000)
            RepChainMgr.Startup4Multi(nodelist(i),nodeport,nodehport)
            nodeport = nodeport+1
            nodehport = nodehport+1
          }
        }catch {
          case e:Exception =>
            println("node port parser error,sample:22522 9081")
        }
      }else{
        println("node parser error,node number more than  4,sample:121000005l35120456.node1,12110107bi45jh675g.node2")
      }
    }else{
      println("parameter is error,sample:121000005l35120456.node1,12110107bi45jh675g.node2 22522 9081")
    }
  }
}
