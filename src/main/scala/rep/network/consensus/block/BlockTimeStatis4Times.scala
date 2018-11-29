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

package rep.network.consensus.block


import org.slf4j.LoggerFactory

object BlockTimeStatis4Times {
  private var  startEndorse:Long = 0
  private var  finishEndorse:Long = 0
  private var  firstRecvEndorse:Long = 0
  private var  isResendEndorse:Boolean = false;
  protected def log = LoggerFactory.getLogger(this.getClass)
  
  private var   finishtime : Array[Long] = new Array[Long](1000)
  private var   firsttime : Array[Long] = new Array[Long](1000)
  private var   count:Integer = 0
  
  /*def setStartEndorse(v:Long)={
    this.startEndorse = v
  }
  
  def setFinishEndorse(v:Long)={
    this.finishEndorse = v
  }
  
  def setFirstRecvEndorse(v:Long)={
    this.firstRecvEndorse = v
  }
  
  def setIsResendEndorse(v:Boolean)={
    this.isResendEndorse = v
  }
  
  def clear={
    this.startEndorse = 0
    this.finishEndorse = 0
    this.firstRecvEndorse = 0
    this.isResendEndorse = false
  }*/
  
  /*def printStatis4Times(modulename:String,clusteraddr:String)={
    finishtime(count) = this.finishEndorse-this.startEndorse
    firsttime(count) = this.firstRecvEndorse-this.startEndorse
    count += 1
    
    log.debug(modulename + " ~ " + s"StartTime=${this.startEndorse},finishTime=${this.finishEndorse},EndorseTime=${this.finishEndorse-this.startEndorse},"
	                + s"--------------FirstTime=${this.firstRecvEndorse},finishSpentTime=${this.firstRecvEndorse-this.startEndorse},"+
	                s"isResend=${this.isResendEndorse}"+" ~ " + clusteraddr);
    if(count>=1000){
      count = 0
      for( i <- 1 until 1000){
        log.debug(modulename + " ~ " + s"statidEndorseTime=${this.finishtime(i)},"
	                + s"--------------statisfinishSpentTime=${this.firsttime(i)}"+" ~ " + clusteraddr);
        this.finishtime(i) = 0
        this.firsttime(i) = 0
      }
    }
  }*/
}