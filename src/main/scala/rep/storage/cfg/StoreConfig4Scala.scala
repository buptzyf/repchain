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

package rep.storage.cfg

import rep.app.conf.SystemProfile
import java.io.File
import rep.storage.util.pathUtil

object StoreConfig4Scala {
  private val dbpath="/repchaindata/data/leveldbdata"
  private val blockpath="/repchaindata/data/blockdata"
  private val filemax=200000000
  
  /**
	 * @author jiangbuyun
	 * @version	1.0
	 * @since	2019-05-11
	 * @category	获取数据库的存储路径
	 * @param	无
	 * @return	String 返回数据库的存储路径
	 * */
	def  getDbPath:String={
		if(SystemProfile.getDBPath == "") dbpath else SystemProfile.getDBPath
	}
	
	/**
	 * @author jiangbuyun
	 * @version	1.0
	 * @since	2019-05-11
	 * @category	根据系统名称，获取数据库的存储路径
	 * @param	SystemName 系统名称
	 * @return	String 返回数据库的存储路径
	 * */
	def getDbPath(SystemName:String):String={
	  if(SystemProfile.getDBPath == "")
	    dbpath + File.separator + SystemName
	  else
	    SystemProfile.getDBPath + File.separator + SystemName
	}
	
	/**
	 * @author jiangbuyun
	 * @version	1.0
	 * @since	2019-05-11
	 * @category	获取区块的存储路径
	 * @param	无
	 * @return	String 返回区块的存储路径
	 * */
	def  getBlockPath:String={
	  if(SystemProfile.getBlockPath == "")
	    blockpath
	  else
	    SystemProfile.getBlockPath
	}
	
	/**
	 * @author jiangbuyun
	 * @version	1.0
	 * @since	2019-05-11
	 * @category	根据系统名称，获取区块的存储路径
	 * @param	SystemName 系统名称
	 * @return	String 返回区块的存储路径
	 * */
	def getBlockPath(SystemName:String):String={
	  if(SystemProfile.getBlockPath == "")
	    blockpath + File.separator + SystemName
	  else
	    SystemProfile.getBlockPath + File.separator + SystemName
	}
	
	def getFileMax:Long={
	  SystemProfile.getFileMax
	}
	
	def getFreeDiskSpace:Long={
		val bpath = this.getBlockPath
		try {
			if(pathUtil.FileExists(bpath) == -1){
				pathUtil.MkdirAll(bpath)
			}
		} catch{
		  case e:Exception => e.printStackTrace()
		}
		val f = new File(bpath)
		f.getFreeSpace()
	}
}