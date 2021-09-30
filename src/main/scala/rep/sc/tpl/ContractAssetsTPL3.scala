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

package rep.sc.tpl

import org.json4s._
import org.json4s.jackson.JsonMethods._
import rep.app.conf.SystemProfile
import rep.protos.peer.ChaincodeId
import rep.utils.IdTool
import rep.sc.scalax.IContract
import rep.sc.Shim

import rep.sc.scalax.ContractContext
import rep.sc.scalax.ContractException
import rep.protos.peer.ActionResult

// class ContractAssetsTPL3 extends IContract {

    //val shim = new Shim()

    // 提交执行
    //def commit(c: ContractContext, ctxId:String, txId:String): ActionResult={
        // val (ctx, blockHeight) = Shim.loadTx(shim, ctxId)
        // val rtx = Shim.loadRemoteTx(shim, ctx.targetChain, txId)
        //if(!verifyProof(proof))
        //   throw ContractException("出块存在性证明验证失败，提交执行失败！")
        // if(rtx.method!=ctx.method || rtx.args!=ctx.args)
            // throw ContractException("不满足执行条件，提交执行失败！")
        
        // Shim.unlockTx(shim, ctxId)

    // }

    // 条件解锁
    //def unlock(c: ContractContext, ctxId:String): ActionResult={
    //    val (ctx, blockHeight) = Shim.loadTx(ctxId)
    //    if(c.t.signCaller.eld != ctx.notify)
    //        throw ContractException("非接收方调用，解锁失败！")
    //    Shim.unlockTx(ctxId)
    //}

    // 过期解锁
    // def expire(c: ContractContext, ctxId: String): ActionResult={
        // val (ctx, blockHeight) = Shim.loadTx(ctxId)
        // if(Shim.getBlockHeight() - blockHeight < ctx.expired)
            // throw ContractException("未达到过期区块高度，解锁失败！")
        // Shim.unlockTx(ctxId)
    // }
// }