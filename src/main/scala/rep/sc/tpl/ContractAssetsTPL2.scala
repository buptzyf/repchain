

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

package rep.sc.tpl

import org.json4s._
import org.json4s.jackson.JsonMethods._
import rep.app.conf.SystemProfile
import rep.protos.peer.ChaincodeId
import rep.utils.IdTool
import java.text.SimpleDateFormat

import rep.sc.scalax.{ContractContext, ContractException, IContract}
import rep.protos.peer.ActionResult

/**
 * 资产管理合约
 */


class ContractAssetsTPL2 extends IContract{
case class Transfer(from:String, to:String, amount:Int)

  // 需要跨合约读账户
  val chaincodeName = SystemProfile.getAccountChaincodeName
  val chaincodeVersion = SystemProfile.getAccountChaincodeVersion 
  //val prefix = IdTool.getCid(ChaincodeId(chaincodeName, chaincodeVersion))

  implicit val formats = DefaultFormats
  
    def init(ctx: ContractContext){      
      println(s"tid: $ctx.t.id")
    }
    
    def set(ctx: ContractContext, data:Map[String,Int]) :ActionResult={
      println(s"set data:$data")
      for((k,v)<-data){
        ctx.api.setVal(k, v)
      }
      null
    }
    
    def transfer(ctx: ContractContext, data:Transfer) :ActionResult={
      if(!data.from.equals(ctx.t.getSignature.getCertId.creditCode))
        throw ContractException("只允许从本人账户转出")
      val signerKey =  data.to
      // 跨合约读账户，该处并未反序列化
      if(ctx.api.getStateEx(chaincodeName,data.to)==null)
        throw ContractException("目标账户不存在")
      val sfrom:Any =  ctx.api.getVal(data.from)
      var dfrom =sfrom.asInstanceOf[Int]
      if(dfrom < data.amount)
        throw ContractException("余额不足")
      var dto = ctx.api.getVal(data.to).toString.toInt
      
      val df:SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
      val t1 = System.currentTimeMillis()      
      val s1 = s"setval begin:${ctx.t.id} " + df.format(t1)
      
      ctx.api.setVal(data.from,dfrom - data.amount)
      //for test 同合约交易串行测试
      Thread.sleep(5000) 
      ctx.api.setVal(data.to,dto + data.amount)
      val t2 = System.currentTimeMillis()      
      val s2 = s"setval end:${ctx.t.id} " + df.format(t2)
      print("\n"+s1+"\n"+s2)
      null
    }
    /**
     * 根据action,找到对应的method，并将传入的json字符串parse为method需要的传入参数
     */
    def onAction(ctx: ContractContext,action:String, sdata:String ):ActionResult={
      val json = parse(sdata)      
      action match {
        case "transfer" => 
          transfer(ctx,json.extract[ Transfer])
        case "set" => 
          set(ctx, json.extract[Map[String,Int]])
      }
    }
    
}

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

package rep.sc.tpl

import org.json4s._
import org.json4s.jackson.JsonMethods._
import rep.app.conf.SystemProfile
import rep.protos.peer.ChaincodeId
import rep.utils.IdTool
import java.text.SimpleDateFormat

import rep.sc.scalax.{ContractContext, ContractException, IContract}
import rep.protos.peer.ActionResult

/**
 * 资产管理合约
 */


class ContractAssetsTPL2 extends IContract{
case class Transfer(from:String, to:String, amount:Int)

  // 需要跨合约读账户
  val chaincodeName = SystemProfile.getAccountChaincodeName
  val chaincodeVersion = SystemProfile.getAccountChaincodeVersion 
  //val prefix = IdTool.getCid(ChaincodeId(chaincodeName, chaincodeVersion))

  implicit val formats = DefaultFormats
  
    def init(ctx: ContractContext){      
      println(s"tid: $ctx.t.id")
    }
    
    def set(ctx: ContractContext, data:Map[String,Int]) :ActionResult={
      println(s"set data:$data")
      for((k,v)<-data){
        ctx.api.setVal(k, v)
      }
      null
    }
    
    def transfer(ctx: ContractContext, data:Transfer) :ActionResult={
      if(!data.from.equals(ctx.t.getSignature.getCertId.creditCode))
        throw ContractException("只允许从本人账户转出")
      val signerKey =  data.to
      // 跨合约读账户，该处并未反序列化
      if(ctx.api.getStateEx(chaincodeName,data.to)==null)
        throw ContractException("目标账户不存在")
      val sfrom:Any =  ctx.api.getVal(data.from)
      var dfrom =sfrom.asInstanceOf[Int]
      if(dfrom < data.amount)
        throw ContractException("余额不足")
      var dto = ctx.api.getVal(data.to).toString.toInt
      
      val df:SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
      val t1 = System.currentTimeMillis()      
      val s1 = s"setval begin:${ctx.t.id} " + df.format(t1)
      
      ctx.api.setVal(data.from,dfrom - data.amount)
      //for test 同合约交易串行测试
      Thread.sleep(5000) 
      ctx.api.setVal(data.to,dto + data.amount)
      val t2 = System.currentTimeMillis()      
      val s2 = s"setval end:${ctx.t.id} " + df.format(t2)
      print("\n"+s1+"\n"+s2)
      null
    }
    /**
     * 根据action,找到对应的method，并将传入的json字符串parse为method需要的传入参数
     */
    def onAction(ctx: ContractContext,action:String, sdata:String ):ActionResult={
      val json = parse(sdata)      
      action match {
        case "transfer" => 
          transfer(ctx,json.extract[ Transfer])
        case "set" => 
          set(ctx, json.extract[Map[String,Int]])
      }
    }
    
}

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

package rep.sc.tpl

import org.json4s._
import org.json4s.jackson.JsonMethods._
import rep.app.conf.SystemProfile
import rep.protos.peer.ChaincodeId
import rep.utils.IdTool
import java.text.SimpleDateFormat

import rep.sc.scalax.{ContractContext, ContractException, IContract}
import rep.protos.peer.ActionResult

/**
 * 资产管理合约
 */


class ContractAssetsTPL2 extends IContract{
case class Transfer(from:String, to:String, amount:Int)

  // 需要跨合约读账户
  val chaincodeName = SystemProfile.getAccountChaincodeName
  val chaincodeVersion = SystemProfile.getAccountChaincodeVersion 
  //val prefix = IdTool.getCid(ChaincodeId(chaincodeName, chaincodeVersion))

  implicit val formats = DefaultFormats
  
    def init(ctx: ContractContext){      
      println(s"tid: $ctx.t.id")
    }
    
    def set(ctx: ContractContext, data:Map[String,Int]) :ActionResult={
      println(s"set data:$data")
      for((k,v)<-data){
        ctx.api.setVal(k, v)
      }
      null
    }
    
    def transfer(ctx: ContractContext, data:Transfer) :ActionResult={
      if(!data.from.equals(ctx.t.getSignature.getCertId.creditCode))
        throw ContractException("只允许从本人账户转出")
      val signerKey =  data.to
      // 跨合约读账户，该处并未反序列化
      if(ctx.api.getStateEx(chaincodeName,data.to)==null)
        throw ContractException("目标账户不存在")
      val sfrom:Any =  ctx.api.getVal(data.from)
      var dfrom =sfrom.asInstanceOf[Int]
      if(dfrom < data.amount)
        throw ContractException("余额不足")
      var dto = ctx.api.getVal(data.to).toString.toInt
      
      val df:SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
      val t1 = System.currentTimeMillis()      
      val s1 = s"setval begin:${ctx.t.id} " + df.format(t1)
      
      ctx.api.setVal(data.from,dfrom - data.amount)
      //for test 同合约交易串行测试
      Thread.sleep(5000) 
      ctx.api.setVal(data.to,dto + data.amount)
      val t2 = System.currentTimeMillis()      
      val s2 = s"setval end:${ctx.t.id} " + df.format(t2)
      print("\n"+s1+"\n"+s2)
      null
    }
    /**
     * 根据action,找到对应的method，并将传入的json字符串parse为method需要的传入参数
     */
    def onAction(ctx: ContractContext,action:String, sdata:String ):ActionResult={
      val json = parse(sdata)      
      action match {
        case "transfer" => 
          transfer(ctx,json.extract[ Transfer])
        case "set" => 
          set(ctx, json.extract[Map[String,Int]])
      }
    }
    
}

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

package rep.sc.tpl

import org.json4s._
import org.json4s.jackson.JsonMethods._
import rep.app.conf.SystemProfile
import rep.protos.peer.ChaincodeId
import rep.utils.IdTool
import java.text.SimpleDateFormat

import rep.sc.scalax.{ContractContext, ContractException, IContract}
import rep.protos.peer.ActionResult

/**
 * 资产管理合约
 */


class ContractAssetsTPL2 extends IContract{
case class Transfer(from:String, to:String, amount:Int)

  // 需要跨合约读账户
  val chaincodeName = SystemProfile.getAccountChaincodeName
  val chaincodeVersion = SystemProfile.getAccountChaincodeVersion 
  //val prefix = IdTool.getCid(ChaincodeId(chaincodeName, chaincodeVersion))

  implicit val formats = DefaultFormats
  
    def init(ctx: ContractContext){      
      println(s"tid: $ctx.t.id")
    }
    
    def set(ctx: ContractContext, data:Map[String,Int]) :ActionResult={
      println(s"set data:$data")
      for((k,v)<-data){
        ctx.api.setVal(k, v)
      }
      null
    }
    
    def transfer(ctx: ContractContext, data:Transfer) :ActionResult={
      if(!data.from.equals(ctx.t.getSignature.getCertId.creditCode))
        throw ContractException("只允许从本人账户转出")
      val signerKey =  data.to
      // 跨合约读账户，该处并未反序列化
      if(ctx.api.getStateEx(chaincodeName,data.to)==null)
        throw ContractException("目标账户不存在")
      val sfrom:Any =  ctx.api.getVal(data.from)
      var dfrom =sfrom.asInstanceOf[Int]
      if(dfrom < data.amount)
        throw ContractException("余额不足")
      var dto = ctx.api.getVal(data.to).toString.toInt
      
      val df:SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
      val t1 = System.currentTimeMillis()      
      val s1 = s"setval begin:${ctx.t.id} " + df.format(t1)
      
      ctx.api.setVal(data.from,dfrom - data.amount)
      //for test 同合约交易串行测试
      Thread.sleep(5000) 
      ctx.api.setVal(data.to,dto + data.amount)
      val t2 = System.currentTimeMillis()      
      val s2 = s"setval end:${ctx.t.id} " + df.format(t2)
      print("\n"+s1+"\n"+s2)
      null
    }
    /**
     * 根据action,找到对应的method，并将传入的json字符串parse为method需要的传入参数
     */
    def onAction(ctx: ContractContext,action:String, sdata:String ):ActionResult={
      val json = parse(sdata)      
      action match {
        case "transfer" => 
          transfer(ctx,json.extract[ Transfer])
        case "set" => 
          set(ctx, json.extract[Map[String,Int]])
      }
    }
    
}
