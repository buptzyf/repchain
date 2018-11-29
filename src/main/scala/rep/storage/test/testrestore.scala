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

package rep.storage.timeAnalysiser

import rep.storage._
import rep.protos.peer._
import rep.utils._


/**
 * @author jiangbuyun
 * @version	0.7
 * @since	2017-09-28
 * */
object testrestore {
  def main(args: Array[String]): Unit = {
       
		   /*val nh = StorageMgr.GetStorageMgr("testSystem")
		   val oh = StorageMgr.GetStorageMgr("1")
		   
		   val oheight = oh.getBlockHeight()
		   var i = 0;
		   for( i <- 1 to 2) {
		     val b = oh.getBlockByHeight(i);
		     val ob = Block.parseFrom(b);
		     nh.restoreBlock(ob)
		   }
		   
			 println("nh_height="+nh.getBlockHeight()+"     nh_txnumber="+nh.getBlockAllTxNumber());
			 println("lh_height="+oh.getBlockHeight()+"     lh_txnumber="+oh.getBlockAllTxNumber());
			 println("nh_c_7db3f156c9666d667614b751336a0588d7cea2c3f0ed0c52c402aaad54352967_account1="+SerializeUtils.deserialise(nh.get("c_7db3f156c9666d667614b751336a0588d7cea2c3f0ed0c52c402aaad54352967_account1"))+
			     "     lh_txnumber="+SerializeUtils.deserialise(oh.get("c_7db3f156c9666d667614b751336a0588d7cea2c3f0ed0c52c402aaad54352967_account1")));*/
		}
}