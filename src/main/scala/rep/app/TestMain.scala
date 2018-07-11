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

package rep.app


import akka.actor.ActorSystem
import akka.actor.Props
import akka.cluster.Cluster
import akka.actor.Address

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import akka.remote.transport.Transport._
import rep.network._
import rep.network.cluster.MemberListener
import rep.network.module.{ModuleManager}
import rep.ui.web.EventServer

import scala.collection.mutable._

object TestMain {
  val systemName = "repChain_"
  val name_PeerManager = "pm_"
  val name_MemberListener = "ml_"
  val system0 = ActorSystem(systemName)
  val joinAddress = Cluster(system0).selfAddress
  var m: Map[Int, ActorSystem] = new HashMap[Int, ActorSystem]();
  var ma: Map[Int, Address] = new HashMap[Int, Address]();

  def startSystem(cout: Int): String = {
    var rs = "{\"status\":\"failed\"}"

    try {
      var i = 1
      for (i <- 1 to cout) {
        if (!m.contains(i)) {
          val nd_system = ActorSystem(systemName)
          Cluster(nd_system).join(joinAddress)
          nd_system.actorOf(Props[MemberListener], name_MemberListener)
          //等待网络稳定
          Thread.sleep(5000)
          nd_system.actorOf(Props[ModuleManager], name_PeerManager + i.toString)
          m += i -> nd_system
          ma += i -> Cluster(nd_system).selfAddress
        }
      }
      rs = "{\"status\":\"success\"}"
    } catch {
      case e: Exception => {
        e.printStackTrace();
      }
    }
    rs
  }

  def stopSystem(from: Int, to: Int): String = {
    var rs = "{\"status\":\"failed\"}"

    try {
      if (from > 0) {
        if (to > from) {
          var i = 0;
          for (i <- from to to) {
            if (m.contains(i) && ma.contains(i)) {
              var ns: ActorSystem = m(i)
              var ad = ma(i)
              if (ns != null) {
                Cluster(ns).down(ad)
                m.remove(i)
                ma.remove(i)
              }
            }
          }
        } else {
          if (m.contains(from) && ma.contains(from)) {
            var ns: ActorSystem = m(from)
            var ad = ma(from)
            if (ns != null) {
              Cluster(ns).down(ad)
              m.remove(from)
              ma.remove(from)
            }
          }
        }
      }
      rs = "{\"status\":\"success\"}"
    } catch {
      case e: Exception => {
        e.printStackTrace();
      }
    }
    rs
  }

  def main(args: Array[String]): Unit = {
    //为方便调试在同一主机join多个node,真实系统同一主机只有一个node,一个EventServer
    //TODO kami 无法真实模拟多System的情况（现阶段各System共用一个单例空间和内存空间。至少应该达到在逻辑上实现分离的情况）
    Cluster(system0).join(joinAddress)
    system0.actorOf(Props[MemberListener], name_MemberListener)
    //等待网络稳定
    Thread.sleep(5000)
    val nd0 = system0.actorOf(Props[ModuleManager], name_PeerManager + "0")
    val ws0 = system0.actorOf(Props[EventServer], "ws")
  }
}
