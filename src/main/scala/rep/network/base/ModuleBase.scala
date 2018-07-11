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

package rep.network.base

import akka.actor.{Actor, Address, ActorRef}
import rep.app.system.ClusterSystem
import rep.network.cluster.ClusterActor
import rep.network.tools.PeerExtension
import rep.network.tools.register.ActorRegister
import rep.utils.{RepLogging, TimeUtils}
import rep.utils.RepLogging.LogTime
import rep.crypto.Sha256
import scala.collection.mutable


/**
  * 模块基础类伴生对象
  *
  * @author shidianyue
  * @version 1.0
  * 
  * @update 2018-05 jiangbuyun
  **/
object ModuleBase {
  def registerActorRef(sysTag: String, actorType: Int, actorRef: ActorRef) = {
    ClusterSystem.getActorRegister(sysTag) match {
      case None =>
        val actorRegister = new ActorRegister()
        actorRegister.register(actorType, actorRef)
        ClusterSystem.register(sysTag, actorRegister)
      case actR =>
        actR.get.register(actorType, actorRef)
    }
  }
}

/**
  * 系统模块基础类
  *
  * @author shidianyue
  * @version 1.0
  * @param name 模块名称
  **/

abstract class ModuleBase(name: String) extends Actor with ModuleHelper with ClusterActor with BaseActor with RepLogging {
  val logPrefix = name

  /**
    * 日志封装
    *
    * @param lOG_TYPE
    * @param msg
    */
  def logMsg(lOG_TYPE: Int, msg: String): Unit = {
    super.logMsg(lOG_TYPE, pe.getSysTag + "-" + name, msg, selfAddr)
  }

  /**
    * 事件时间戳封装
    *
    * @param msg
    * @param step
    * @param actorRef
    */
  def logTime(msg: String, step: Int, actorRef: ActorRef): Unit = {
    actorRef ! LogTime(pe.getSysTag + "-" + name, s"Step_${step} - " + msg, TimeUtils.getCurrentTime(), selfAddr)
  }
}

/**
  * 模块帮助功能接口
  *
  * @author shidianyue
  * @version 1.0
  **/
trait ModuleHelper extends Actor {
  val pe = PeerExtension(context.system)

  /**
    * 从注册中心获取actor引用
    * @param sysTag
    * @param actorType
    * @return
    */
  def getActorRef(sysTag: String, actorType: Int): ActorRef = {
    ClusterSystem.getActorRegister(sysTag).getOrElse(None) match {
      case None => self
      case actorReg: ActorRegister => actorReg.getActorRef(actorType).getOrElse(None) match {
        case None => self
        case actorRef: ActorRef => actorRef
      }
    }
  }

  /**
    * 从注册中心获取actor引用
    * @param actorType
    * @return
    */
  def getActorRef(actorType: Int): ActorRef = {
    ClusterSystem.getActorRegister(pe.getSysTag).getOrElse(None) match {
      case None => self
      case actorReg: ActorRegister => actorReg.getActorRef(actorType).getOrElse(None) match {
        case None => self
        case actorRef: ActorRef => actorRef
      }
    }
  }

  /**
    * 向注册中心注册actor引用
    * @param sysTag
    * @param actorType
    * @param actorRef
    * @return
    */
  def registerActorRef(sysTag: String, actorType: Int, actorRef: ActorRef) = {
    ClusterSystem.getActorRegister(sysTag) match {
      case None =>
        val actorRegister = new ActorRegister()
        actorRegister.register(actorType, actorRef)
        ClusterSystem.register(sysTag, actorRegister)
      case actR =>
        actR.get.register(actorType, actorRef)
    }
  }

  /**
    * 向注册中心注册actor引用
    * @param actorType
    * @param actorRef
    * @return
    */
  def registerActorRef(actorType: Int, actorRef: ActorRef) = {
    ClusterSystem.getActorRegister(pe.getSysTag) match {
      case None =>
        val actorRegister = new ActorRegister()
        actorRegister.register(actorType, actorRef)
        ClusterSystem.register(pe.getSysTag, actorRegister)
      case actR =>
        actR.get.register(actorType, actorRef)
    }
  }
}