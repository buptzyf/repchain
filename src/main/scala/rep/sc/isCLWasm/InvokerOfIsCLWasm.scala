package rep.sc.isCLWasm

import org.json4s.{JArray, JBool, JDouble, JInt, JObject, JString}
import org.json4s.jackson.JsonMethods.parse
import org.wasmer.exports.Function
import org.wasmer.{Instance, Module}
import rep.proto.rc2.ActionResult
import rep.sc.scalax.ContractContext

import java.util
import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable

/**
 * 在wasmer中执行交易
 *
 * @author JayTsang
 * @since 2023-04-01
 * */
class InvokerOfIsCLWasm(utils: Utils) {
  // 合约业务初始化方法名
  private val INIT_FUNCTION_NAME = "init"
  // 合约方法执行环境初始化方法名
  private val ENV_INIT_FUNCTION_NAME = "_init"
  // 合约方法执行环境收集方法名
  private val ENV_COLLECT_FUNCTION_NAME = "_terminate"
  // 合约内置申请内存空间方法名
  private val MALLOC_FUNCTION_NAME = "_allocate"
  // 合约方法执行结果状态
  private val SUCCESS = true
  private val FAILED = false
  // 合约上下文环境变量，合约调用者账户ID
  private val CTX_SENDER = "sender"
  // 合约方法返回值/输出参数保留名
  private val FUNCTION_OUT_PARAM_NAME = "_out"

  def invokeOfInit(module: Module, abi: JObject, ctx: ContractContext, param: java.util.List[String]): ActionResult = {
    invoke(module, abi, ctx, INIT_FUNCTION_NAME)
  }

  def onAction(module: Module, abi: JObject, ctx: ContractContext): ActionResult = {
    invoke(module, abi, ctx)
  }

  private def invoke(module: Module, abi: JObject, ctx: ContractContext): ActionResult = {
    invoke(module: Module, abi: JObject, ctx: ContractContext, ctx.t.para.ipt.get.function)
  }

  // TODO: remove functionArgs
  private def invoke(module: Module, abi: JObject, ctx: ContractContext, action: String) = {
    val arInstance: AtomicReference[Instance] = new AtomicReference[Instance]()
    val instance: Instance = module.instantiate()
    arInstance.set(instance)

    // Prepare the arguments to invoke the wasm smart contract function
    // val argPointers: util.List[Integer] = new util.ArrayList[Integer]()
    try {
      val malloc = arInstance.get.exports.getFunction(MALLOC_FUNCTION_NAME)
      val memory = arInstance.get.exports.getMemory("memory")
      val mbf = memory.buffer

      val storageVariables = abi.obj(0)._2.asInstanceOf[JObject]
      val structures = abi.obj(1)._2.asInstanceOf[JObject]
      val structuresMap = mutable.HashMap[String, JArray]()
      structures.obj.foreach { case (name, structure) =>
        structuresMap(name) = structure.asInstanceOf[JArray]
      }

      // 检查合约方法调用信息是否有效
      val contractFunctions = abi.obj(2)._2.asInstanceOf[JObject]
      val contractFunctionOption = contractFunctions.obj.find { case (name, _) => name.equals(action) }
      if (contractFunctionOption.isEmpty) {
        throw new Exception(s"Error when executing the chaincode function:${action},msg=no such method")
      }
      val contractFunction = contractFunctionOption.get
      // 合约方法的json描述结构示例如下:
      // {
      //    "functionName": {
      //      "name": "functionName",
      //      "params": [
      //        { "type": "int", "name": "_a", "isUsed": true },
      //        { "type": "double", "name": "_b", "isUsed": true }
      //        { "type": "struct", "struct": "_string", "name": "_c", "isUsed": true }
      //       ]
      //     }
      // }
      // 检查输入的合约方法参数与定义的合约方法参数是否匹配
      var args = ctx.t.para.ipt.get.args
      val params = contractFunction._2.asInstanceOf[JObject].obj(1)._2.asInstanceOf[JArray].arr
      // 检查合约方法的最后一个参数是否为返回值/输出参数，判断参数数量是否匹配
      val lastParamName = params.last.asInstanceOf[JObject].obj.find(field => field._1.equals("name")).get._2.asInstanceOf[JString].s
      val hasReturnParam = lastParamName.equals(FUNCTION_OUT_PARAM_NAME)
      if (
        (hasReturnParam && args.length + 1 != params.length)
          || (!hasReturnParam && args.length != params.length)
      ) {
        throw new Exception(s"Wrong input parameters to call the chaincode function:${action}, which requires ${params.length - 1} parameters, but got ${args.length}")
      }
      if (hasReturnParam) {
        // 添加返回值/输出参数占位
        args = args :+ ""
      }
      // 变换合约方法参数形式，输入参数为json字符串形式，需要对其进行转换，输出/返回值参数需要被初始化为指针类型，
      // 1. 针对复合类型参数，将json字符串形式的输入参数转为已序列化的二进制形式，再将其反序列化解析为WebAssembly内的C数据结构，写入WebAssembly内存，得到参数指针
      // 2. 针对输出/返回值参数，使用与针对复合类型参数相同的方法
      // 3. 针对基础类型参数（Int, Double, Bool），将json字符串形式的输入参数转为WebAssembly可接受的Int,Double,Int类型
      val convertedArgs = args.zipWithIndex.map { case (arg, index) =>
        var param = params(index).asInstanceOf[JObject]
        // 针对指针类型的合约方法输入参数，将其转为非指针类型以便于申请内存
        if (utils.isPointerDataType(param.obj(0)._2.asInstanceOf[JString].s)
          && !(hasReturnParam && index == params.length - 1)
        ) {
          param = JObject(
            ("type", JString(utils.dePointerDataType(param.obj(0)._2.asInstanceOf[JString].s)))
              ::param.obj.drop(1)
          )
        }
        //
        if (utils.isComplexDataType(param.obj(0)._2.asInstanceOf[JString].s)
          || (hasReturnParam && index == params.length - 1)
        ) {
          var serializedArg: Array[Byte] = null
          // 针对返回值/输出参数构建默认值
          if (hasReturnParam && index == params.length - 1) {
            serializedArg = utils.genDefaultSerializedData(null, param, structuresMap)
          } else {
            serializedArg = utils.json2Binary(parse(arg), param, structuresMap)
          }
          val argPointer = utils.mallocWrap(malloc, utils.getSize(param, structuresMap))
          utils.deserialize(
            param,
            structuresMap,
            argPointer,
            mbf,
            0,
            serializedArg,
            malloc
          )
          argPointer
        } else {
          parse(arg) match {
            case JInt(i) => i.toInt
            case JDouble(d) => d
            case JBool(b) => if (b) 1 else 0
            case _ => throw new Exception(s"Wrong input parameters to call the chaincode function:${action}, not supported parameter type for: ${arg}")
          }
        }
      }.asInstanceOf[Seq[Object]]

      /* 准备合约内置环境初始化方法参数以初始化执行结果变量、合约状态变量、执行上下文变量等 */

      // 创建并初始化合约方法执行结果变量，初始值为true，若合约方法执行失败该变量值会被置为false
      val success = utils.writeBool(malloc, mbf, SUCCESS)
      // 创建并初始化合约方法执行结果信息变量
      val msg = utils.writeString(malloc, mbf, "Success")

      // 反序列化已持久化的world states变量以及合约上下文变量, 写入WebAssembly内存，返回变量指针
      val variablePointers = storageVariables.obj.map { case (name, structure) =>
        // 合约上下文变量特殊处理
        if (name.equals(CTX_SENDER)) {
          utils.writeString(malloc, mbf, ctx.t.signature.get.certId.get.creditCode)
        }
        else {
          val data = ctx.api.getVal(name)
          val variablePointer = utils.mallocWrap(malloc, utils.getSize(structure.asInstanceOf[JObject], structuresMap))
          var dataBytes: Array[Byte] = null
          // 无已持久化数据时需要填充默认数据
          if (data == null) {
            dataBytes = utils.genDefaultSerializedData(null, structure.asInstanceOf[JObject], structuresMap)
          } else {
            dataBytes = data.asInstanceOf[Array[Byte]]
          }
          utils.deserialize(
            structure.asInstanceOf[JObject],
            structuresMap,
            variablePointer,
            mbf,
            0,
            dataBytes,
            malloc
          )
          variablePointer
        }
      }
      val envArgs = (success::msg::variablePointers).toArray
      // 将上述变量指针作为参数，调用isCL内置的合约环境初始化方法
      val initEnv = arInstance.get.exports.get(ENV_INIT_FUNCTION_NAME).asInstanceOf[Function]
      initEnv.apply(envArgs:_*)

      // 调用执行目标合约方法
      val wasmFunction = arInstance.get.exports.get(action).asInstanceOf[Function]
      wasmFunction.apply(convertedArgs.toArray:_*)

      // 调用isCL内置的合约环境收集方法，获取已被更新的合约环境变量
      val endEnv = arInstance.get.exports.get(ENV_COLLECT_FUNCTION_NAME).asInstanceOf[Function]
      endEnv.apply(envArgs:_*)

      // 检查执行结果是否成功
      if (utils.readBool(mbf, success) == FAILED) {
        throw new Exception(s"Error when executing the chaincode function:${action}, with parameters=${args.mkString(",")} error msg=${utils.readString(mbf, msg)} ")
      }

      // 序列化WebAssembly合约中的world states变量，并持久化
      storageVariables.obj.zipWithIndex.foreach { case ((name, structure), index) =>
        // 忽略合约上下文变量
        if (!name.equals(CTX_SENDER)) {
          val variablePointer = variablePointers(index)
          val dataBytes = utils.serialize(
            structure.asInstanceOf[JObject],
            structuresMap,
            variablePointer,
            mbf
          )._1
          ctx.api.setVal(name, dataBytes)
        }
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
        throw new Exception(s"Failed to invoke the method:${action},msg=${ctx.api.getMessage},err=${e.getMessage}")
    } finally {
      // 释放参数所占WebAssembly内存
      // 实际上目前没必要单独释放参数内存，instance.close()会释放所有WebAssembly内存
      //      if (argPointers != null) {
      //        IntStream.range(0, argPointers.size).forEach(index => {
      //          arInstance.get().exports.getFunction("deallocate").apply(Integer.valueOf(argPointers.get(index)))
      //        })
      //      }
      if (instance != null) {
        try {
          instance.close()
        } catch {
          case em: Exception => em.printStackTrace()
        }
      }
    }
    new ActionResult(0, "")
  }
}
