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

package rep.crypto


import java.util.concurrent.locks._
import scala.collection.immutable
import java.security.cert.{ Certificate, CertificateFactory }
import rep.storage._
import rep.utils.SerializeUtils

object certCache {
  private val  getCertLock : Lock = new ReentrantLock();
  private var  caches : immutable.HashMap[String,Certificate] = new immutable.HashMap[String,Certificate]()
  
  def getCertForUser(certKey:String,sysTag:String):Certificate={
    var rcert : Certificate = null
    getCertLock.lock()
    try{
      if(caches.contains(certKey)){
        rcert = caches(certKey)
      }else{
        val sr: ImpDataAccess = ImpDataAccess.GetDataAccess(sysTag)
        val cert = Option(sr.Get(certKey))
        if (cert != None){
          if (!(new String(cert.get)).equalsIgnoreCase("null")) {
                val kvcert = SerializeUtils.deserialise(cert.get).asInstanceOf[Certificate]
                if(kvcert != null){
                  caches += certKey -> kvcert
                  rcert = kvcert
                }
            }
        }
      }
    }finally {
      getCertLock.unlock()
    }
    rcert
  }
}