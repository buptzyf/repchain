package rep.storage.cfg

import rep.app.conf.SystemProfile
import java.io.File
import rep.storage.util.pathUtil

object StoreConfig4Scala {
  /**
	 * @author jiangbuyun
	 * @version	1.0
	 * @since	2019-05-11
	 * @category	获取数据库的存储路径
	 * @param	无
	 * @return	String 返回数据库的存储路径
	 * */
	def  getDbPath:String={
		SystemProfile.getDBPath
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