package rep.sc.isCLWasm

import org.wasmer.Module
import rep.storage.util.pathUtil

import java.io.{File, FileInputStream, FileOutputStream}

/**
 * 加载编译Wasm字节码合约的相关工具
 *
 * @author Jaytsang
 * @since 2023-04-22
 * */
object CompilerOfIsCLWasm {
  val path_source = pathUtil.getPath("custom_contract/wasm")
  /**
   * 将Wasm字节码合约编译为机器代码，并保存到文件以便复用
   *
   * @author Jaytsang
   * @since 2023-04-22
   * @param code     Wasm字节码合约
   * @param fileName 保存的文件名
   * @return Wasm Module实例
   * */
  def compileAndSave(code: Array[Byte], fileName: String): Module = {
    var m: Module = null
    try {
      if (!Module.validate(code)) {
        throw new Exception("Invalid wasm smart contract code")
      }
      m = compile(code)
      val cmb = m.serialize()
      saveCode(pathUtil.Join(path_source, fileName), cmb)
      m
    } catch {
      case e: Exception =>
        throw new Exception(s"CompileAndSave failed,msg=${e.getMessage}")
    }
  }

  /**
   * 判断是否已存在编译好的Wasm合约机器代码
   *
   * @author Jaytsang
   * @since 2023-04-22
   * @param fileName 已保存Wasm合约机器代码的文件名
   * @return
   * */
  def existedCompiledWasm(fileName: String): Boolean = {
    val f = new File(pathUtil.Join(path_source, fileName))
    f.exists()
  }

  /**
   * 从已保存文件中加载已编译好的Wasm合约机器代码
   *
   * @author Jaytsang
   * @since 2023-04-22
   * @param fileName 已保存Wasm合约机器代码的文件名
   * @return Wasm Module实例
   * */
  def loadCompiledWasmFromFile(fileName: String): Module = {
    var m: Module = null
    var fileInputStream: FileInputStream = null
    try {
      fileInputStream = new FileInputStream(pathUtil.Join(path_source, fileName))
      val code = fileInputStream.readAllBytes
      m = Module.deserialize(code)
      m
    } catch {
      case e: Exception =>
        throw new Exception(s"loadCompiledWasmFromFile failed,msg=${e.getMessage}")
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
   * 将Wasm字节码合约编译为机器代码，生成Wasm Module实例
   *
   * @author Jaytsang
   * @since 2023-04-22
   * @param code wasm合约字节码
   * @return Wasm Module实例
   * */
  private def compile(code: Array[Byte]): Module = {
    try {
      val module = new Module(code)
      module
    } catch {
      case e: Exception =>
        throw new Exception(s"compile wasm failed=${e.getMessage}")
    }
  }

  /**
   * 保存已编译的Wasm机器代码到文件中
   *
   * @author Jaytsang
   * @since 2023-04-22
   * @param filePath 欲保存的文件路径
   * @code 已编译好的Wasm机器代码
   * @return
   * */
  private def saveCode(filePath: String, code: Array[Byte]): Unit = {
    var outputStream: FileOutputStream = null
    try {
      outputStream = new FileOutputStream(filePath)
      outputStream.write(code)
    } catch {
      case e: Exception =>
        throw new Exception(s"SaveCode exception ,msg=${e.getMessage}")
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
