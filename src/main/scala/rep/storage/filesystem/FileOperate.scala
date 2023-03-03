package rep.storage.filesystem

import java.io.File
import scala.util.control.Breaks.{break, breakable}

/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-07
 * @category	文件目录操作类。
 * */
object FileOperate {
  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-07
   * @category	建立指定待目录，如果已经存证就跳过
   * @param	path String 待建立目录
   * @return	返回建立目录的结果
   * */
  def MkdirAll(path: String): Boolean = {
    var b = false
    try {
      val f = new File(path)
      if (f.exists()) {
        b = true
      }else{
        b = f.mkdirs
      }
    } catch {
      case e: Exception =>
        throw e
    }
    b
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-07
   * @category	删除指定目录以及目录下的所有文件和目录
   * @param	path String 待删除目录，输入必须是目录，否则返回失败
   * @return	返回删除目录的结果
   * */
  def RemoveDirectory(path: String): Boolean = {
    var b = false
    try{
      if (delAllFile(path)) { //删除完里面所有内容
        val pf = new File(path)
        b = pf.delete //删除空文件夹
      }
    }catch {
      case e: Exception =>
          throw e
    }
    b
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-07
   * @category	删除指定目录下的所有文件和目录，不删除该目录本身
   * @param	path String 需要删除内容的目录，输入必须是存在的目录
   * @return	返回删除的结果
   * */
  private def delAllFile(path: String): Boolean = {
    var b = false
    try{
      val file = new File(path)
      if (!file.exists) return b
      if (!file.isDirectory) return b
      var isError = false
      val tempList = file.list

      breakable(tempList.foreach(fn=>{
        val tmpFile = if (path.endsWith(File.separator)){
          new File(path +  fn)
        }else{
          new File(path + File.separator + fn)
        }
        if(tmpFile.isFile && !tmpFile.delete()){
          isError = true
          break
        }
        if(tmpFile.isDirectory && !delAllFile(tmpFile.getAbsolutePath)){
          isError = true
          break
        }
      }))

      if (!isError) b = true
    }catch {
      case e:Exception=>
        throw e
    }

    b
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-07
   * @category	将输入数组的内容拼接程完整的文件路径
   * @param	src:Array[String]
   * @return	返回String文件路径
   * */
  def mergeFilePath(src:Array[String]):String={
    val r = new StringBuffer()
    src.foreach(str=>{
      if(!str.endsWith(File.separator)){
        r.append(str).append(File.separator)
      }else{
        r.append(str)
      }
    })
    r.toString
  }
}
