package rep.network.consensus.endorse

import akka.actor.{ActorRef, Address, Props}
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import rep.network.base.ModuleBase
import rep.protos.peer.{Transaction}
import scala.util.control.Breaks._

object verifySign4Endorment {
    def props(name: String): Props = Props(classOf[ verifySign4Endorment ], name)
    
    case class verifySign4Transcation(blkhash:String,ts:Array[Transaction],startPos:Integer,len:Integer,actoridex:Integer)
    case class verifySignResult(blkhash:String,resultflag:Boolean,startPos:Integer,lenght:Integer,actoridex:Integer)
}

class verifySign4Endorment(moduleName: String) extends ModuleBase(moduleName) {
    import context.dispatcher
    import scala.concurrent.duration._
    import akka.actor.ActorSelection 
    import rep.storage.ImpDataAccess
    import rep.network.consensus.block.BlockHelper
    
    val sr: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
  
    override def receive = {
      case  verifySign4Endorment.verifySign4Transcation(blkhash,ts,startPos,len,actoridex) =>
        var r : Boolean = true
        var count : Integer = 0
        
        breakable(
            while(count < len) {
              if(!BlockHelper.checkTransaction(ts(startPos+count), sr)){
                  r = false
                  break
              }
              count += 1
            }
        )
        
        if(!r){
          sender ! verifySign4Endorment.verifySignResult(blkhash,false,startPos,len,actoridex)
        }else{
          sender ! verifySign4Endorment.verifySignResult(blkhash,true,startPos,len,actoridex)
        }
    }
}