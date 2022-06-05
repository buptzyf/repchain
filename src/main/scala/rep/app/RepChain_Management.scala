package rep.app

import akka.actor.{ActorSystem}
import rep.app.management.{ManagementServer, ReasonOfStartup, RepChainMgr}

object RepChain_Management {
  def main(args: Array[String]): Unit = {
    if(args!=null && args.length>2){
      val port_str = args(0)
      val port = Integer.parseInt(port_str)
      val ssl_str = args(1)
      val ssl_mode = Integer.parseInt(ssl_str)
      val useClientAuth_str = args(2)
      val useClientAuth = if(useClientAuth_str.equalsIgnoreCase("true")) true else false
      val system = ActorSystem("RepChain-Management-Server")
      system.actorOf(ManagementServer.props(port,ssl_mode,useClientAuth), "ManagementServer")
      if(args.length > 3){
        for(i<-3 to args.length-1){
          System.out.println(s"Start start node(${args(i)})...")
          RepChainMgr.Startup4Single(args(i),ReasonOfStartup.Manual)
          System.out.println(s"Now start to check whether the node(${args(i)}) is started successfully...")
          System.out.println(s"Node(${args(i)}) , startup result=${RepChainMgr.systemStatus(args(i))}")
        }
      }
    }else{
      System.out.println("Please enter Service port number and ssl mode,use ClientAuth to start，0=http;1=https;2=https(gmssl),use ClientAuth is Boolean," +
        "Node name is optional，the system will start the node automatically， for example：RepChain_Management 8080 0 [nodeName]")
    }
  }
}
