package rep.app

import akka.actor.{ActorSystem}
import rep.app.management.{ManagementServer, ReasonOfStartup, RepChainMgr}

object RepChain_Management {
  def main(args: Array[String]): Unit = {
    if(args!=null && args.length>0){
      val port_str = args(0)
      val port = Integer.parseInt(port_str)
      val system = ActorSystem("RepChain-Management-Server")
      system.actorOf(ManagementServer.props(port), "ManagementServer")
      if(args.length > 1){
        for(i<-1 to args.length-1){
          System.out.println(s"Start start node(${args(i)})...")
          RepChainMgr.Startup4Single(args(i),ReasonOfStartup.Manual)
          System.out.println(s"Now start to check whether the node(${args(i)}) is started successfully...")
          System.out.println(s"Node(${args(i)}) , startup result=${RepChainMgr.systemStatus(args(i))}")
        }
      }
    }else{
      System.out.println("Please enter Service port number to start，" +
        "Node name is optional，the system will start the node automatically， for example：RepChain_Management 8080 [nodeName]")
    }
  }
}
