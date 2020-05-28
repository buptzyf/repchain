package rep.api.rest

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorRef

object getApiActorOfRest {
  var l : AtomicInteger = new AtomicInteger(0)
  def getActor(ra:Array[ActorRef]):ActorRef={
    ra(l.getAndAdd(1) % ra.length)

  }
}
