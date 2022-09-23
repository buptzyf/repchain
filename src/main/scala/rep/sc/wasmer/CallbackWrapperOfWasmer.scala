package rep.sc.wasmer

import org.wasmer.{Imports, Instance, Type}
import rep.sc.Shim
import java.nio.ByteBuffer
import java.util
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference
import scala.util.control.Breaks.{break, breakable}
import org.wasmer.Module

/**
 * @author jiangbuyun
 * @version 2.0
 * @since 2022-09-22
 * @category 建立wasmer回调实例。
 * */
object CallbackWrapperOfWasmer {
  private val SUCCESS: Int = 0
  private val FAILED: Int = -1

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-09-22
   * @category 从指定位置读取数据
   * @param ptr:Int 开始位置, buffer:ByteBuffer 存放数据的缓存
   * @return 返回读取的数据的字符串
   * */
  private def getStringFromByteBuffer (ptr:Int, buffer:ByteBuffer):String ={
    val sb = new java.lang.StringBuilder()
    val max = buffer.limit() - 1
    breakable(
      for (i<-ptr to max)
      {
        buffer.position(i);
        val b = buffer.get();
        if (b == 0) {
          break
        }else{
          sb.appendCodePoint(b);
        }
      }
    )
    sb.toString()
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-09-22
   * @category 建立wasmer回调实例
   * @param module:Module, context:Shim, arInstance:AtomicReference[Instance]
   * @return 返回回调实例
   * */
  def create(module:Module, context:Shim, arInstance:AtomicReference[Instance]):Imports = {
    var imp : Imports = null
    val specGetStateSize = new Imports.Spec(
      "env",
      "getValueSizeByKey",
      argv => {
        try {
          val memory = arInstance.get().exports.getMemory("memory")
          val keyPtr = argv.get(0).intValue()
          val mbf = memory.buffer()
          val key = getStringFromByteBuffer(keyPtr, mbf)
          val vb = context.getValForBytes(key)
          if (vb == null) {
            argv.set(0, FAILED)
          } else {
            val valueSize = vb.length
            argv.set(0, valueSize)
          }
        } catch  {
          case e:Exception=>
              argv.set(0, FAILED)
        }
        argv
      },
      Collections.singletonList(Type.I32),
      Collections.singletonList(Type.I32)
    );
    val specGetState = new Imports.Spec(
      "env",
      "getValueByKey",
      argv => {
        try {
          val memory = arInstance.get().exports.getMemory("memory");
          val keyPtr = argv.get(0).intValue()
          val valuePtr = argv.get(1).intValue()
          val mbf = memory.buffer()
          val key = getStringFromByteBuffer(keyPtr, mbf)
          val value = context.getValForBytes(key)
          if (value == null) {
            argv.set(0, FAILED)
          } else {
            mbf.position(valuePtr)
            mbf.put(value)
            argv.set(0, SUCCESS)
          }
        } catch  {
          case e: Exception =>
            argv.set(0, FAILED)
        }
        argv
      },
      util.Arrays.asList(Type.I32, Type.I32),
      Collections.singletonList(Type.I32)
    );
    val specSetState = new Imports.Spec(
      "env",
      "setValueByKey",
      argv => {
        try {
          val memory = arInstance.get().exports.getMemory("memory");
          val keyPtr = argv.get(0).intValue()
          val valuePtr = argv.get(1).intValue()
          val valueSize = argv.get(2).intValue()
          val mbf = memory.buffer()
          val key = getStringFromByteBuffer(keyPtr, mbf)
          val value = new Array[Byte](valueSize)
          mbf.position(valuePtr)
          mbf.get(value, 0, valueSize)
          context.setVal(key, value)
          argv.set(0, SUCCESS)
        } catch {
          case e: Exception =>
            argv.set(0, FAILED)
        }
        argv
      },
      util.Arrays.asList(Type.I32, Type.I32, Type.I32),
      Collections.singletonList(Type.I32)
    )
    val specLogInfo = new Imports.Spec(
      "env",
      "logInfo",
      argv => {
        try {
          val memory = arInstance.get().exports.getMemory("memory")
          val infoPtr = argv.get(0).intValue()
          val mbf = memory.buffer()
          val info = getStringFromByteBuffer(infoPtr, mbf)
          context.setMessage(info)
          argv.set(0, SUCCESS)
        } catch  {
          case e: Exception =>
            argv.set(0, FAILED)
        }
        argv
      },
      Collections.singletonList(Type.I32),
      Collections.singletonList(Type.I32)
    )

    imp = Imports.from(util.Arrays.asList(
      specGetStateSize, specGetState, specSetState, specLogInfo
    ), module)

    imp
  }
}
