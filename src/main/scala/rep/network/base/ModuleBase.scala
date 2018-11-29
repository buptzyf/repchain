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

package rep.network.base

import akka.actor.{Actor, Address, ActorRef}
import rep.app.system.ClusterSystem
import rep.network.cluster.ClusterActor
import rep.network.tools.PeerExtension
import rep.network.tools.register.ActorRegister
import rep.crypto.ShaDigest
import scala.collection.mutable
import rep.log.trace.RepLogHelp
import rep.log.trace.LogType
import org.slf4j.LoggerFactory
import rep.log.trace.RepTimeTrace


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

abstract class ModuleBase(name: String) extends Actor with ModuleHelper with ClusterActor with BaseActor{
  val logPrefix = name
  protected def log = LoggerFactory.getLogger(this.getClass)
  val ptt :RepTimeTrace = RepTimeTrace.getRepTimeTrace()
  /**
    * 日志封装
    *
    * @param lOG_TYPE
    * @param msg
    */
  def logMsg(lOG_TYPE: LogType, msg: String): Unit = {
    RepLogHelp.logMsg(log,lOG_TYPE, pe.getSysTag + "-"  + msg + " ~ " + selfAddr, pe.getSysTag)
  }

  /**
    * 事件时间戳封装
    *
    * @param msg
    * @param step
    * @param actorRef
    */
  def logTime(timetag:String,time:Long,isstart:Boolean): Unit = {
    ptt.addTimeTrace(pe.getSysTag, timetag, time, isstart)
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