package rep.app

import akka.actor.ActorSystem
import rep.app.management.{ManagementServer, ReasonOfStartup, RepChainMgr}
import rep.app.system.RepChainSystemContext

object RepChain_Management {
  def main(args: Array[String]): Unit = {
      //val config : RepChainConfig = new RepChainConfig("management")
      val ctx = new RepChainSystemContext("management")
    RepChainSystemContext.setCtx("management",ctx)
      val system = ActorSystem("RepChain-Management-Server",ctx.getConfig.getSystemConf)
      system.actorOf(ManagementServer.props(ctx.getConfig), "ManagementServer")
      if(args.length > 0){
        for(i<-0 to args.length-1){
          System.out.println(s"Start start node(${args(i)})...")
          RepChainMgr.Startup4Single(args(i),ReasonOfStartup.Manual)
          System.out.println(s"Now start to check whether the node(${args(i)}) is started successfully...")
          System.out.println(s"Node(${args(i)}) , startup result=${RepChainMgr.systemStatus(args(i))}")
        }
      }
  }
}
