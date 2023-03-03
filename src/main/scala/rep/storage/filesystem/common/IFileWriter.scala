package rep.storage.filesystem.common

/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-10
 * @category	定义文件写接口
 * */
abstract class IFileWriter(fileName:String) extends IFile {
  def writeData(startpos: Long, bb: Array[Byte]): Boolean
  def deleteBytesFromFileTail(delLength:Long):Boolean
  def getFileLength:Long
}
