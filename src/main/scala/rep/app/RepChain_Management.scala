package rep.app

import akka.actor.{ActorSystem, Props}
import rep.app.management.ManagementServer

object RepChain_Management {
  def main(args: Array[String]): Unit = {
    if(args!=null && args.length>0){
      val port_str = args(0)
      val port = Integer.parseInt(port_str)
      val system = ActorSystem("RepChain-Management-Server")
      system.actorOf(ManagementServer.props(port), "ManagementServer")
    }
  }
}
