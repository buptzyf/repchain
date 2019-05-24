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

package rep.storage


/**
 * @author jiangbuyun
 * @version	0.7
 * @since	2017-09-28
 * */
object IdxPrefix {
     val  Height:String   = "rechain_height"
	   val  MaxFileNo:String  = "rechain_file_no"
	   val  FirstHeightOfFilePrefix = "repchain_file_"
	   val  FirstHeightOfFileSuffix = "_first_height"
	   val  TotalAllTxNumber:String = "rechain_total_all_tx_number"
	    //每次存储时都需要修改这个内容
	   val  IdxBlockPrefix:String = "b_b_"//存储内容为blockindex完整值
	   val  IdxBlockHeight:String = "b_h_"//存储内容为block对应的hash值
	   val  IdxTransaction:String = "tx_"//存储内容为对应的block的hash值
	   val  WorldStateKeyPreFix:String = "c_"//存储内容为每个链码的worldstate
	   
}