/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Fintech Research Center of ISCAS.
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
 */

package rep.network.consensus

import akka.actor.{Actor, ActorRef, Props}
import com.typesafe.config.Config
import rep.network.consensus.CRFD.{ConsensusInitFinish, InitCRFD}
import rep.network.consensus.ConsensusManager.ConsensusType

/**
  * Created by shidianyue on 2017/9/23.
  */

object ConsensusManager {
  def props(name:String, config: Config): Props = Props(classOf[ ConsensusManager ],name, config)
  case object ConsensusType {
    val CRFD = 1
    val OTHER = 2
  }

}

class ConsensusManager(name:String, conf: Config) extends Actor{

  private val consensusType = init(conf)
  private var consensusActor:ActorRef = null

  generateConsensus()

  initConsensus()

  def init(config: Config): Int = {
    val typeConsensus = config.getString("system.consensus.type")
    typeConsensus match {
      case "CRFD" =>
        ConsensusManager.ConsensusType.CRFD
      case _ =>
        //ignore
        ConsensusManager.ConsensusType.OTHER
    }
  }

  def generateConsensus() = {
    consensusType match {
      case ConsensusType.CRFD =>
        consensusActor = context.actorOf(CRFD.props("consensus-CRFD"),"consensus-CRFD")
      case ConsensusType.OTHER => //ignore
    }
  }

  def initConsensus() ={
    consensusType match {
      case ConsensusType.CRFD =>
        consensusActor ! InitCRFD
    }
  }

  def startConsensus = ???

  override def receive: Receive = {

    case ConsensusInitFinish =>
      context.parent ! ConsensusInitFinish

    case _ => //ignore
  }
}
