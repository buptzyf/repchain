package rep.storage.filesystem.common


/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-10
 * @category	定义文件访问接口
 * */
trait IFile {
  def FreeResource:Unit
  def getLastAccessTime:Long
  def getFileName:String
  def getFileNo:Int
}
