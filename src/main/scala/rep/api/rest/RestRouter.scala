package rep.api.rest

import java.util.concurrent.atomic.AtomicLong

import akka.actor.{ActorRef, ActorSystem}

class RestRouter(ActorNumber:Int,system: ActorSystem) {
  private var ras: Array[ActorRef] = new Array[ActorRef](ActorNumber)
  private val nextActor : AtomicLong = new AtomicLong(0)

  CreateActor

  private def CreateActor={
    for (i <- 0 to ActorNumber - 1) {
      var ra = system.actorOf(RestActor.props("api_" + i).withDispatcher("http-dispatcher"), "api_"+i)
      ras(i) = ra
    }
  }

  def getRestActor:ActorRef={
    val size = ras.length
    val index = (nextActor.getAndIncrement % size).asInstanceOf[Int]
    ras(if (index < 0) size + index else index)
  }
}
