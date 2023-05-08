package rep.sc.isCLWasm

import org.apache.commons.codec.binary.BinaryCodec
import org.wasmer.Module
import rep.storage.util.pathUtil

import java.io.{File, FileInputStream, FileOutputStream}

/**
 * @author jiangbuyun
 * @version 2.0
 * @since 2022-09-22
 * @category 对编译wasm成字节码的代码进行编译，把字节码编译成执行指令。
 * */
object CompilerOfIsCLWasm {

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-09-22
   * @category 编译wasm字节码到执行指令，并保存执行指令到文件
   * @param code : String 交易获取的合约代码采用ascii字符串,fn:String 保存的文件名
   * @return 返回Module实例
   * */
  def CompileAndSave(code: String, fn: String): Module = {
    // TODO: Specify the encoding charset?
    val code_byte = new BinaryCodec().decode(code.getBytes())
    CompileAndSave(code_byte, fn)
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-09-22
   * @category 编译wasm字节码到执行指令，并保存执行指令到文件
   * @param code : Array[Byte] 合约字节码,fn:String 保存的文件名
   * @return 返回Module实例
   * */
  def CompileAndSave(code: Array[Byte], fn: String): Module = {
    var m: Module = null
    val path_source = pathUtil.getPath("custom_contract/wasm")
    try {
      if (!Module.validate(code)) {
        throw new Exception("Invalid wasm smart contract code")
      }
      m = Compile(code)
      val cmb = m.serialize()
      saveCode(path_source + "/" + fn, cmb)
      m
    } catch {
      case e: Exception =>
        throw new Exception(s"CompileAndSave failed,msg=${e.getMessage}")
    }
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-09-22
   * @category 合约的执行指令文件是否存在
   * @param fn :String 合约执行指令的文件名
   * @return true：执行指令文件存在；false：不存在
   * */
  def isCompiled(fn:String):Boolean={
    val path_source = pathUtil.getPath("custom_contract/wasm")
    val f = new File(path_source + "/" + fn)
    f.exists()
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-09-22
   * @category 从文件中直接读取合约的执行指令文件
   * @param fn :String 保存的文件名
   * @return 返回Module实例
   * */
  def CompileFromFile(fn: String): Module = {
    var m: Module = null
    val path_source = pathUtil.getPath("custom_contract/wasm")
    var fileInputStream: FileInputStream = null
    try {
      fileInputStream = new FileInputStream(path_source + "/" + fn)
      val code = fileInputStream.readAllBytes
      m = Module.deserialize(code)
      m
    } catch {
      case e: Exception =>
        throw new Exception(s"CompileFromFile failed,msg=${e.getMessage}")
    } finally {
      if (fileInputStream != null) {
        try {
          fileInputStream.close()
        } catch {
          case el: Exception =>
            el.printStackTrace()
        }
      }
    }
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-09-22
   * @category 编译字节码
   * @param code : Array[Byte] 合约字节码
   * @return 返回Module实例
   * */
  private def Compile(code: Array[Byte]): Module = {
    try {
      val module = new Module(code)
      module
    } catch {
      case e: Exception =>
        throw new Exception(s"Compiled wasm file exception，msg=${e.getMessage}")
    }
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-09-22
   * @category 保存执行指令
   * @param fn :String 保存的文件名,code:Array[Byte] 执行指令
   * @return
   * */
  private def saveCode(fn: String, code: Array[Byte]): Unit = {
    var outputStream: FileOutputStream = null
    try {
      outputStream = new FileOutputStream(fn)
      outputStream.write(code)
    } catch {
      case e: Exception =>
        throw new Exception(s"Save wasm file exception ,msg=${e.getMessage}")
    } finally {
      if (outputStream != null) {
        try {
          outputStream.close()
        } catch {
          case el: Exception =>
            el.printStackTrace()
        }
      }
    }
  }

}
