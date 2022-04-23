/*
 * Copyright  2019 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
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

package rep.log

import akka.actor.{ActorRef}
import akka.stream.{Attributes, Outlet}
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import akka.stream.SourceShape
import rep.log.RecvEventActor.Register
import rep.proto.rc2.Event

class EventActor4Stage(eventactor: ActorRef) extends GraphStage[SourceShape[Event]]{
//class EventActor4Stage(system: ActorSystem) extends GraphStage[SourceShape[Event]]{
  import scala.concurrent.duration._
  
  //val evtactor = system.actorOf(Props[RecvEventActor],"RecvEventActor_"+IdTool.getUUID)
  
  val out: Outlet[Event] = Outlet("EventActor4Stage")
  override def shape: SourceShape[Event] = SourceShape(out)
  
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =  new GraphStageLogic(shape) {
    implicit def self = stageActor.ref

    override def preStart(): Unit = {
      val thisStageActor = getStageActor(messageHandler).ref
      //evtactor ! Register(thisStageActor)
      eventactor ! Register(thisStageActor)
    }

    setHandler(out,new OutHandler{
         override def onPull():Unit={
           //此处被messageHandler取代
         }
       })
    
    private def messageHandler(receive: (ActorRef, Any)): Unit = {
      receive match {
        case (_, evt:Event) => {
          if(this.isAvailable(out) && !this.isClosed(out) ){
            push(out,evt)
          }
        }
        case(_,_) =>
      }
    }
  }
  
}