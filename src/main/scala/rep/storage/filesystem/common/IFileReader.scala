package rep.storage.filesystem.common

/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-10
 * @category	定义文件读接口
 * */
abstract class IFileReader(fileName:String) extends IFile {
  def readData(startpos: Long, length: Int):Array[Byte]
}
