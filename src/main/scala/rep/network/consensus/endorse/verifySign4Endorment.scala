/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BA SIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package rep.network.consensus.endorse

import akka.actor.{ActorRef, Address, Props}
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import rep.network.base.ModuleBase
import rep.protos.peer.{Transaction}
import scala.util.control.Breaks._
import rep.log.trace.LogType


object verifySign4Endorment {
    def props(name: String): Props = Props(classOf[ verifySign4Endorment ], name)
    
    case class verifySign4Transcation(blkhash:String,ts:Array[Transaction],startPos:Integer,len:Integer,actoridex:Integer,sendtime:Long)
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
      case  verifySign4Endorment.verifySign4Transcation(blkhash,ts,startPos,len,actoridex,sendtime) =>
        var r : Boolean = true
        var count : Integer = 0
        val tsize = ts.length
        
        breakable(
            while(count < len) {
              if(startPos+count < tsize){
                if(!BlockHelper.checkTransaction(ts(startPos+count), pe.getSysTag)){
                    r = false
                    break
                }
              }else{
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
        logMsg(LogType.INFO,s"+++++++verify sign time=${System.currentTimeMillis() - sendtime},handle count=${len},actoridex=${actoridex}")
    }
}