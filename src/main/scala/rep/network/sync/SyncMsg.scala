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

package rep.network.sync

import rep.protos.peer._
import akka.actor.{ ActorRef, Props }

object SyncMsg {
  case class StartSync(isNoticeModuleMgr:Boolean)
  
  case class ChainInfoOfRequest(height:Long)
  
  case class ResponseInfo(response: BlockchainInfo, responser: ActorRef,ChainInfoOfSpecifiedHeight:BlockchainInfo,responsername:String)
  
  case class BlockDataOfRequest(startHeight:Long)
  
  case class BlockDataOfResponse(data: Block)
  
  case class  SyncRequestOfStorager(responser:ActorRef,maxHeight:Long)
  
  case class AnalysisResult(ar:Int,error:String)//0=失败、1=成功、2=数量不够
  
  case class SynchAction(start:Long,end:Long,server:ActorRef)
  
  case class RollbackAction(destHeight:Long)

}