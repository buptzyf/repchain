package rep.sc.wasmer

import org.json4s._
import org.json4s.jackson.JsonMethods._
import rep.proto.rc2.ActionResult
import rep.sc.Shim
import org.wasmer.{Instance, Module}
import org.wasmer.exports.Function
import rep.sc.scalax.ContractContext
import java.nio.charset.StandardCharsets
import java.util
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.{Collectors, IntStream}

/**
 * @author jiangbuyun
 * @version 2.0
 * @since 2022-09-22
 * @category 在wasmer中执行交易。
 * */
object invokerOfWasmer {
  implicit val formats = DefaultFormats
  private val init_function_name = "init"

  def invokeOfInit(module: Module,ctx: ContractContext):ActionResult={
    val shim = ctx.api
    val action = init_function_name
    val json = parse(ctx.t.para.spec.get.initParameter)
    invoke(module, shim, action, json.extract[java.util.ArrayList[String]])
  }

  def onAction(module: Module,ctx: ContractContext):ActionResult={
    val shim = ctx.api
    val ipt = ctx.t.para.ipt.get
    val action = ipt.function
    //获得传入参数
    val data = ipt.args
    val functionArgs = new util.ArrayList[String]()
    data.foreach(d=>{
      functionArgs.add(d)
    })
    invoke(module, shim, action, functionArgs)
  }

  private def invoke(module: Module, shim: Shim, action: String, functionArgs: java.util.List[String]): ActionResult = {
    var result: ActionResult = null
    val arInstance: AtomicReference[Instance] = new AtomicReference[Instance]()
    val imports = CallbackWrapperOfWasmer.create(module, shim, arInstance)
    val instance: Instance = module.instantiate(imports)
    arInstance.set(instance)

    // To invoke the wasm smart contract method
    var argPointers: java.util.List[Integer] = null
    try {
      // Convert the String type args to pointers pointing to the wasm memory space
      argPointers = new util.ArrayList[Integer]()
      functionArgs.forEach(arg=>{
        val stringBytes = arg.getBytes(StandardCharsets.UTF_8)
        val stringBytesWithNullTerminated = new Array[Byte](stringBytes.length + 1)
        System.arraycopy(stringBytes, 0, stringBytesWithNullTerminated, 0, stringBytes.length)
        val obj = arInstance.get().exports.getFunction("allocate").apply(Integer.valueOf(stringBytesWithNullTerminated.length))(0)
        val ptr = obj.asInstanceOf[Integer]
        val memory = arInstance.get().exports.getMemory("memory")
        val mbf = memory.buffer()
        mbf.position(ptr)
        mbf.put(stringBytesWithNullTerminated)
        argPointers.add(ptr)
      })
        /*functionArgs.stream().map(arg => {
        val stringBytes = arg.getBytes(StandardCharsets.UTF_8)
        val stringBytesWithNullTerminated = new Array[Byte](stringBytes.length + 1)
        System.arraycopy(stringBytes, 0, stringBytesWithNullTerminated, 0, stringBytes.length)
        val obj = arInstance.get().exports.getFunction("allocate").apply(Integer.valueOf(stringBytesWithNullTerminated.length))(0)
        val ptr = obj.asInstanceOf[Integer]
        val memory = arInstance.get().exports.getMemory("memory")
        val mbf = memory.buffer()
        mbf.position(ptr)
        mbf.put(stringBytesWithNullTerminated)
        ptr
      }).collect(Collectors.toList())*/

      val wasmFunction = instance.exports.get(action).asInstanceOf[Function]

      val ret = wasmFunction.apply(argPointers.toArray())(0).asInstanceOf[Integer]

      if (ret != 0) {
        throw new Exception(s"Error when executing the chaincode method:${action},msg=${shim.getMessage} ")
      }
    } catch {
      case e: Exception =>
        throw new Exception(s"Failed to invoke the method:${action},msg=${shim.getMessage},err=${e.getMessage}")
    } finally {
      // Free the space for the args in the wasm memory
      if (argPointers != null) {
        IntStream.range(0, argPointers.size).forEach(index => {
          arInstance.get().exports.getFunction("deallocate").apply(Integer.valueOf(argPointers.get(index)),
            Integer.valueOf(functionArgs.get(index).length() + 1)
          )
        })
      }
    }

    result = new ActionResult(0, "")
    result
  }
}
