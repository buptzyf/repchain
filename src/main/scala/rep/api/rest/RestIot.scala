package rep.api.rest

import akka.actor.{Actor, Props}
import rep.network.base.ModuleHelper
import rep.protos.peer.Transaction
import rep.utils.GlobalUtils.ActorType
import rep.utils.RepLogging


object RestIot {
  def props(name: String): Props = Props(classOf[RestIot], name)
  case class tranSign(tran: Array[Byte])


}

/**
  * @author zyf
  */
class RestIot extends Actor with ModuleHelper with RepLogging{

  def receive = {

    /**
      * 这个目前用来接受带签名的交易
      */
    case trs : RestIot.tranSign => {
      val txr = Transaction.parseFrom(trs.tran)
      getActorRef(ActorType.TRANSACTION_POOL) ! txr
      println(txr)
    }
  }
}
