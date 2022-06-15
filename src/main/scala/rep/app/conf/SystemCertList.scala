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

package rep.app.conf

import java.security.cert.Certificate
import java.util.concurrent.atomic.AtomicBoolean

import rep.app.system.RepChainSystemContext
import rep.crypto.cert.CertificateUtil
import rep.log.RepLogger

import scala.collection.mutable.HashMap

/**
 * @author jiangbuyun
 * @version	0.7
 * @category	获取信任的证书列表，抽签时从此文件中获取。
 * */

class SystemCertList(ctx:RepChainSystemContext) {
  private var mySystemCertList:Set[String] = (new scala.collection.mutable.ArrayBuffer[String]()).toSet[String]

  private val isChangeCertList : AtomicBoolean = new AtomicBoolean(false)
  private var certList : Array[String] = null
  private val lock : Object = new Object

  loadCertListFromConfig

  private def loadCertListFromConfig:Unit={
    if(this.mySystemCertList.isEmpty){
      val tmpMap = CertificateUtil.loadTrustCertificate(this.ctx)
      val list = ctx.getConfig.getVoteNodeList
      val cList = tmpMap.keySet.toArray
      this.mySystemCertList = checkCertList(cList)
      RepLogger.trace(RepLogger.System_Logger, "SystemCertList 初始化装载="+this.mySystemCertList.mkString(","))
    }
  }

  private def checkCertList(inputList : Array[String]):Set[String]={
    var rList : scala.collection.mutable.ArrayBuffer[String] = new scala.collection.mutable.ArrayBuffer[String]()
    val list = ctx.getConfig.getVoteNodeList
    inputList.foreach(name=>{
      if(list.contains(name)){
        rList += name
      }
    })
    rList.toSet[String]
  }

  def updateCertList(update:Array[String]):Unit={
    //if(update != null && update.length >= ctx.getConfig.getMinVoteNumber){
      this.lock.synchronized({
        this.certList = update
        this.isChangeCertList.set(true)
      })
    RepLogger.trace(RepLogger.System_Logger, "SystemCertList 更新通知接收="+this.certList.mkString(","))
    //}
  }

  def getVoteList:Set[String] = {
    if(this.isChangeCertList.get()){
      this.lock.synchronized({
        if(this.certList != null){
          this.mySystemCertList = checkCertList(this.certList)
        }
        this.isChangeCertList.set(false)
        RepLogger.trace(RepLogger.System_Logger, "SystemCertList 更新装载="+this.mySystemCertList.mkString(","))
        this.mySystemCertList
      })
    }else{
      this.mySystemCertList
    }

  }

}