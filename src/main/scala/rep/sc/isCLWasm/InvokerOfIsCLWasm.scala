package rep.sc.isCLWasm

import org.json4s.{DefaultFormats, JArray, JBool, JDouble, JInt, JObject, JString}
import org.json4s.jackson.JsonMethods.parse
import org.wasmer.exports.Function
import org.wasmer.{Instance, Module}
import rep.proto.rc2.{ActionResult, Transaction}
import rep.sc.scalax.ContractContext

import java.nio.{ByteBuffer, ByteOrder}
import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable

object InvokerOfIsCLWasm {
  // 合约业务初始化方法名
  val INIT_FUNCTION_NAME = "init"
  // 合约执行环境初始化方法名(调用执行合约方法前初始化全局变量状态)
  val ENV_INIT_FUNCTION_NAME = "_init"
  // 合约执行环境收集方法名(调用执行合约方法完成后收集全局变量状态)
  val ENV_COLLECT_FUNCTION_NAME = "_terminate"
  // 合约内置申请内存空间方法名
  val MALLOC_FUNCTION_NAME = "_allocate"
  // 合约方法执行结果状态
  val SUCCESS = true
  val FAILED = false
  // 合约上下文环境变量，合约调用者账户ID
  val CTX_SENDER = "sender"
  // 合约方法返回值/输出参数保留名
  val FUNCTION_OUT_PARAM_NAME = "_out"

  implicit val formats = DefaultFormats

  // world state全局变量存入底层数据库时的变量名后缀，防止合约开发者使用变量名与合约元数据名冲突
  val STORAGE_VARIABLE_NAME_SUFFIX = "_storage"
}

/**
 * 在wasmer中执行交易
 *
 * @author JayTsang
 * @since 2023-04-01
 * */
class InvokerOfIsCLWasm(utils: Utils) {
  import InvokerOfIsCLWasm._

  def onAction(module: Module, abi: JObject, ctx: ContractContext): ActionResult = {
    ctx.t.`type` match {
      case Transaction.Type.CHAINCODE_DEPLOY =>
        invokeOfInit(module, abi, ctx)
      case _ =>
        invoke(module, abi, ctx)
    }
  }

  def invokeOfInit(module: Module, abi: JObject, ctx: ContractContext): ActionResult = {
    val initParams = ctx.t.para.spec.get.initParameter
    // 若提供初始化方法参数，则调用初始化方法(针对部署合约交易)
    if (initParams != null && !initParams.isEmpty) {
      val initParamsJson = parse(initParams)
      val initParamsStrings = initParamsJson.extract[List[String]]
      invoke(module, abi, ctx, INIT_FUNCTION_NAME, initParamsStrings)
    }
    ActionResult()
  }

  private def invoke(module: Module, abi: JObject, ctx: ContractContext): ActionResult = {
    val params = ctx.t.para.ipt.get.args
    invoke(module: Module, abi: JObject, ctx: ContractContext, ctx.t.para.ipt.get.function, params)
  }

  private def invoke(module: Module, abi: JObject, ctx: ContractContext, action: String, paramsStrings: Seq[String]) = {
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
      var args = paramsStrings
      val params = contractFunction._2.asInstanceOf[JObject].obj(1)._2.asInstanceOf[JArray].arr
      // 检查合约方法的最后一个参数是否为返回值/输出参数，判断参数数量是否匹配
      val lastParamName = params.last.asInstanceOf[JObject].obj.find(field => field._1.equals("name")).get._2.asInstanceOf[JString].s
      val hasReturnParam = lastParamName.equals(FUNCTION_OUT_PARAM_NAME)
      if (
        (hasReturnParam && args.length + 1 != params.length)
          || (!hasReturnParam && args.length != params.length)
      ) {
        throw new Exception(s"Wrong input parameters to call the chaincode function:${action}, which requires ${ if (hasReturnParam) params.length - 1 else params.length} parameters, but got ${args.length}")
      }
      if (hasReturnParam) {
        // 添加返回值/输出参数占位
        args = args :+ ""
      }
      // 变换合约方法参数形式，输入参数为json字符串形式，需要对其进行转换，同时输出/返回值参数需要被初始化为指针类型，
      // 1. 针对复合类型输入参数，将json字符串形式的输入参数转为已序列化的二进制形式，再将其反序列化解析为WebAssembly内的C数据结构，写入WebAssembly内存，得到参数指针
      // 2. 针对输出/返回值参数，使用与针对复合类型参数相同的方法
      // 3. 针对基础类型输入参数（Int, Double, Bool），将json字符串形式的输入参数转为WebAssembly可接受的Int,Double,Int类型
      val convertedArgs = args.zipWithIndex.map { case (arg, index) =>
        var param = params(index).asInstanceOf[JObject]
        // 针对指针类型的合约方法输入参数，将其转为非指针类型以便于申请内存
        if (utils.isPointerDataType(param.obj(0)._2.asInstanceOf[JString].s)
          && !(hasReturnParam && index == params.length - 1)
        ) {
          param = JObject(
            ("type", JString(utils.dePointerDataType(param.obj(0)._2.asInstanceOf[JString].s)))
              :: param.obj.drop(1)
          )
        }
        // 针对复合类型的合约方法输入参数和输出/返回值参数
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
          // 针对基础类型的合约方法输入参数
          parse(arg) match {
            case JInt(i) => i.toInt
            case JDouble(d) => d
            case JBool(b) => if (b) 1 else 0
            case _ => throw new Exception(s"Wrong input parameters to call the chaincode function:${action}, not supported parameter type for: ${arg}")
          }
        }
      }.asInstanceOf[Seq[Object]]

      /* 准备合约内置的环境初始化方法参数和环境收集方法参数以初始化和收集执行结果变量、合约状态变量、执行上下文变量等 */

      // 创建并初始化合约方法执行结果变量，初始值为true，若合约方法执行失败该变量值会被置为false
      //      val success = utils.writeBool(malloc, mbf, SUCCESS)
      // isCL的设计由对success参数始终使用指针传递改为在合约环境初始化方法中值传递，在合约环境收集方法中指针传递
      val successToInitEnv = 1
      val successPointerToEndEnv = utils.mallocWrap(malloc, 1)
      // 创建并初始化合约方法执行结果信息变量
      val msg = utils.writeString(malloc, mbf, "Success")

      // 构建全局变量（world state + context）合约环境初始化方法参数及合约环境收集方法参数：
      // 1. 相应变量若是复合类型，则在WebAssembly内存中申请空间并反序列化相应变量数据到该地址空间，返回该地址指针
      // 2. 相应变量若是基础类型(Int,Double,Bool)，则
      //    a)对于合约环境初始化方法，直接将该变量数据转为WebAssembly可接受的Int,Double,Bool值;
      //    b)对于合约环境收集方法，在WebAssembly内存中为相应变量申请空间，返回该地址指针
      // 3. 合约上下文变量需要特殊处理
      val variableArgs = storageVariables.obj.map { case (name, structure) =>
        // 针对合约上下文变量特殊处理
        if (name.equals(CTX_SENDER)) {
          val ctxSender = utils.writeString(malloc, mbf, ctx.t.signature.get.certId.get.creditCode)
          (ctxSender, ctxSender)
        } else {
          val data = ctx.api.getVal(name + STORAGE_VARIABLE_NAME_SUFFIX)
          // 无已持久化数据时需要填充默认数据
          var dataBytes: Array[Byte] = null
          if (data == null) {
            dataBytes = utils.genDefaultSerializedData(null, structure.asInstanceOf[JObject], structuresMap)
          } else {
            dataBytes = data.asInstanceOf[Array[Byte]]
          }
          val typeName = structure.asInstanceOf[JObject].obj(0)._2.asInstanceOf[JString].s
          // 针对基础类型变量
          if (!utils.isComplexDataType(typeName)) {
            typeName match {
              case "int" =>
                (
                  ByteBuffer.wrap(dataBytes).order(ByteOrder.LITTLE_ENDIAN).getInt(),
                  utils.mallocWrap(malloc, utils.getSize(structure.asInstanceOf[JObject], structuresMap))
                )
              case "double" =>
                (
                  ByteBuffer.wrap(dataBytes).order(ByteOrder.LITTLE_ENDIAN).getDouble(),
                  utils.mallocWrap(malloc, utils.getSize(structure.asInstanceOf[JObject], structuresMap))
                )
              case "bool" =>
                (
                  if (ByteBuffer.wrap(dataBytes).order(ByteOrder.LITTLE_ENDIAN).get().toInt == 1) true else false,
                  utils.mallocWrap(malloc, utils.getSize(structure.asInstanceOf[JObject], structuresMap))
                )
            }
          } else {
            val variablePointer = utils.mallocWrap(malloc, utils.getSize(structure.asInstanceOf[JObject], structuresMap))

            utils.deserialize(
              structure.asInstanceOf[JObject],
              structuresMap,
              variablePointer,
              mbf,
              0,
              dataBytes,
              malloc
            )
            (variablePointer, variablePointer)
          }
        }
      }
      // 调用isCL内置的合约环境初始化方法
      // 该方法对基础类型输入参数，是使用值传递，对复合类型参数使用指针传递
      val initEnvArgs = (successToInitEnv :: msg :: variableArgs.map{ case (item,_) => item }).toArray.asInstanceOf[Array[Object]]
      val initEnv = arInstance.get.exports.get(ENV_INIT_FUNCTION_NAME).asInstanceOf[Function]
      initEnv.apply(initEnvArgs: _*)

      // 调用执行目标合约方法
      val wasmFunction = arInstance.get.exports.get(action).asInstanceOf[Function]
      wasmFunction.apply(convertedArgs.toArray: _*)

      // 调用isCL内置的合约环境收集方法，获取已被更新的合约环境变量
      // 该方法包括基础类型在内的所有输入参数都是使用指针传递
      val endEnvArgs = (successPointerToEndEnv :: msg :: variableArgs.map{ case (_,item) => item }).toArray
      val endEnv = arInstance.get.exports.get(ENV_COLLECT_FUNCTION_NAME).asInstanceOf[Function]
      endEnv.apply(endEnvArgs: _*)

      // 检查执行结果是否成功
      if (utils.readBool(mbf, successPointerToEndEnv) == FAILED) {
        throw new Exception(s"Error when executing the chaincode function:${action}, with parameters=${args.mkString(",")} error msg=${utils.readString(mbf, msg)} ")
      }

      // 序列化WebAssembly合约中的world states变量，并持久化
      storageVariables.obj.zipWithIndex.foreach { case ((name, structure), index) =>
        // 忽略合约上下文变量
        if (!name.equals(CTX_SENDER)) {
          val variablePointer = variableArgs(index)._2
          val dataBytes = utils.serialize(
            structure.asInstanceOf[JObject],
            structuresMap,
            variablePointer,
            mbf
          )._1
          ctx.api.setVal(name + STORAGE_VARIABLE_NAME_SUFFIX, dataBytes)
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
