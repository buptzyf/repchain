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

package rep.sc.scalax

import rep.protos.peer.Transaction
import rep.protos.peer.ActionResult
import rep.sc.IShim

final class ContractContext(val api:IShim, val t:Transaction)
final case class ContractException(private val message: String = "", private val cause: Throwable = None.orNull)
  extends Exception(message, cause) 

/**
 * @author c4w
 */

trait IContract {
  def init(ctx: ContractContext)
  def onAction(ctx: ContractContext,action:String, sdata:String ):ActionResult
}

abstract class Contract {
  
  def init(ctx: ContractContext)
  def onAction(ctx: ContractContext ,action:String, sdata:String):ActionResult
}