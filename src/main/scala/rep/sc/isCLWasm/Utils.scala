package rep.sc.isCLWasm

import org.json4s.{DefaultFormats, Extraction, JArray, JBool, JDouble, JInt, JString}
import org.json4s.JsonAST.{JObject, JValue}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.{ByteBuffer, ByteOrder}
import org.wasmer.exports.Function

import java.security.MessageDigest
import java.util.Base64
import scala.collection.mutable
import scala.util.control.Breaks._

class Utils {
  case class SmartContract(isCLVersion: Int, code: Array[Byte], abi: String);

  /**
   * 解析base64编码的合约代码参数以获取智能合约信息，
   * 该合约代码参数按顺序包含了下列数据:
   * - 合约语言isCl版本号(4字节小端序表示)
   * - 合约代码大小(即字节数，4字节小端序表示)
   * - 合约代码(wasm字节码，可变大小)
   * - 合约Json字符串ABI(Application Binary Interface)信息大小(即字节数，4字节小端序表示)
   * - 合约Json字符串ABI(Application Binary Interface)信息(表示合约方法signature及状态数据结构的json字符串，可变大小)
   * - 校验信息(对前述所有信息的sha256sum校验值)
   *
   * @param codePackage base64编码的字符串形式的合约代码参数
   * @return 解析得到的智能合约信息
   */
  def parseSmartContract(codePackage: String): SmartContract = {
    try {
      val bytes = Base64.getDecoder().decode(codePackage)
      val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
      val isCLVersion = buffer.getInt()
      val scSize = buffer.getInt()
      val scWASM: Array[Byte] = new Array[Byte](scSize)
      buffer.get(scWASM, 0, scWASM.length)
      val scABISize = buffer.getInt()
      val scABIBytes: Array[Byte] = new Array[Byte](scABISize)
      buffer.get(scABIBytes, 0, scABIBytes.length)
      // Json字符串ABI信息本身是UTF_8编码
      val scABI = new String(scABIBytes, UTF_8)

      // 验证sha256校验值信息
      val dataToBeChecked: Array[Byte] = new Array[Byte](buffer.position())
      buffer.slice(0, buffer.position()).get(dataToBeChecked, 0, dataToBeChecked.length)
      val checksum: Array[Byte] = new Array[Byte](32)
      buffer.get(checksum, 0, checksum.length)
      val digest = MessageDigest.getInstance("SHA-256")
      digest.update(dataToBeChecked)
      val checksumCalculated = digest.digest();
      if (!checksumCalculated.sameElements(checksum)) {
        throw new Exception("failed to verify the checksum of the deploying smart contract")
      }

      SmartContract(isCLVersion, scWASM, scABI)
    } catch {
      case e: Exception =>
        throw new Exception(s"Failed to parse SmartContract, msg=${e.getMessage}")
    }
  }

  // For c compiled to wasm-32
  private val basicDataTypeSize: mutable.Map[String, Int] = mutable.Map(
    "char" -> 1,
    "bool" -> 1,
    "short" -> 2,
    "int" -> 4,
    "long" -> 4,
    "long long" -> 8,
    "float" -> 4,
    "double" -> 8,
    // 使用pointer指代所有类型的指针
    "pointer" -> 4
  )

  private val ComplexDataTypePrefix = "struct"
  private val PointerDataTypeSuffix = "*"
  private val StringDataTypePrefix = "_string"
  private val ListDataTypePrefix = "_list_"
  private val MapDataTypePrefix = "_map_"

  def isComplexDataType(dataType: String): Boolean = dataType != null && dataType.startsWith(ComplexDataTypePrefix)

  def isPointerDataType(dataType: String): Boolean = dataType != null && dataType.endsWith(PointerDataTypeSuffix)

  def dePointerDataType(dataType: String): String = dataType.trim.dropRight(1)

  def isStringDataType(dataType: String): Boolean = dataType != null && dataType.startsWith(StringDataTypePrefix)

  def isListDataType(dataType: String): Boolean = dataType != null && dataType.startsWith(ListDataTypePrefix)

  def isMapDataType(dataType: String): Boolean = dataType != null && dataType.startsWith(MapDataTypePrefix)

  case class BasicType(dataType: String, name: String)

  case class ComplexType(dataType: String, struct: String, name: String)

  implicit val formats = DefaultFormats

  /**
   * 反序列化二进制数据到WebAssembly内存中以构造isCL编译后的C语言变量
   *
   * @param structure           欲被反序列化的数据类型的Json描述，如{ "type": "int", "name": "_capacity" }, { "type": "struct", "struct": "_map_string_string", "name": "mss" }
   * @param structuresMap       当前WebAssembly中所有已知复合数据结构的Json描述
   * @param writePointer        处理当前数据时在WebAssembly内存中的写指针
   * @param memBuffer           代表WebAssembly内存的ByteBuffer(字节序应已被设置为小端模式)
   * @param readIndex           当前从data字节数组中读取数据的index
   * @param data                欲被反序列化的二进制数据,字节数组
   * @param malloc              在WebAssembly中申请分配内存的方法，应是由WebAssembly实例程序自身导出的方法
   * @param itemCountForPointer 当isCL编译后的C语言源码中复合类型中有指针属性时，
   *                            该指针指向的内存不仅只存储了单个相应类型数据，而可能是若干个相同类型数据，具体数量由该复合类型的第一个属性值即`_capacity`来表示，
   *                            在反序列化时需要利用该值来请求分配内存
   * @return 元组: (在WebAssembly内存中的写指针, 从data读取数据的index)
   */
  def deserialize(
                   structure: JObject,
                   structuresMap: mutable.HashMap[String, JArray],
                   writePointer: Int,
                   memBuffer: ByteBuffer,
                   readIndex: Int,
                   data: Array[Byte],
                   malloc: Function,
                   itemCountForPointer: Int = 1
                 ): (Int, Int) = {
    val varType = structure.obj(0)._2.asInstanceOf[JString].s
    val varName = structure.obj.find(field => field._1.equals("name")).get._2.asInstanceOf[JString].s

    // 处理除指针外的基本类型数据
    if (!isComplexDataType(varType) && !isPointerDataType(varType)) {
      val varSize = getSize(structure, structuresMap)
      memBuffer.put(writePointer, data.slice(readIndex, readIndex + varSize))
      return (writePointer + varSize, readIndex + varSize)
    }

    // 处理指针类型数据
    if (isPointerDataType(varType)) {
      val dataType = varType.dropRight(1)
      var dataStructure: JObject = null
      if (isComplexDataType(dataType)) {
        val dataStruct = structure.obj(1)._2.asInstanceOf[JString].s
        dataStructure = Extraction.decompose(ComplexType(dataType, dataStruct, varName)).asInstanceOf[JObject]
      } else {
        dataStructure = Extraction.decompose(BasicType(dataType, varName)).asInstanceOf[JObject]
      }
      val dataPointer = mallocWrap(
        malloc,
        Integer.valueOf(getSize(dataStructure, structuresMap) * itemCountForPointer)
      )
      var writePointerOfItem = dataPointer
      var newReadIndex = readIndex
      for (_ <- 1 to itemCountForPointer) {
        val res = deserialize(dataStructure, structuresMap, writePointerOfItem, memBuffer, newReadIndex, data, malloc)
        writePointerOfItem = res._1
        newReadIndex = res._2
      }

      // 将指针变量转为小端序字节数组
      val dataPointerBuffer = ByteBuffer.allocate(4)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putInt(dataPointer)

      memBuffer.put(writePointer, dataPointerBuffer.array())
      return (writePointer + getSize(structure, structuresMap), newReadIndex)
    }

    // 处理复合类型数据
    val varStruct = structure.obj(1)._2.asInstanceOf[JString].s
    val structStructure = structuresMap.get(varStruct) match {
      case Some(refStructure) => refStructure.arr
      case None => throw new Error(s"Wrong data type ${varStruct}")
    }
    var newItemCountForPointer = itemCountForPointer
    var newWritePointer = writePointer
    var newReadIndex = readIndex
    val startWritePointer = writePointer
    for (fieldStructure <- structStructure) {
      if (isStringDataType(varStruct) || isListDataType(varStruct) || isMapDataType(varStruct)) {
        newItemCountForPointer = memBuffer.slice(startWritePointer, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()
      }
      val res = deserialize(
        fieldStructure.asInstanceOf[JObject],
        structuresMap,
        getUpperPointer(
          newWritePointer,
          getAlignSize(fieldStructure.asInstanceOf[JObject], structuresMap)
        ),
        memBuffer,
        newReadIndex,
        data,
        malloc,
        newItemCountForPointer
      )
      newWritePointer = res._1
      newReadIndex = res._2
    }
    (newWritePointer, newReadIndex)
  }

  /**
   * 从wasm内存中获取变量序列化后的二进制数据
   *
   * @param structure           欲被序列化的数据结构的Json描述，如{ "type": "int", "name": "_capacity" }, { "type": "struct", "struct": "_map_string_string", "name": "mss" }
   * @param structuresMap       当前WebAssembly中所有已知复合数据结构的Json描述
   * @param readPointer         处理当前数据时在WebAssembly内存中的读指针
   * @param memBuffer           代表WebAssembly内存的ByteBuffer(字节序应已被设置为小端模式)
   * @param itemCountForPointer 当isCL编译后的C语言源码中复合类型中有指针属性时，
   *                            该指针指向的内存不仅只存储了单个相应类型数据，而可能是若干个相应类型数据，具体数量由该复合类型的第一个属性来表示，
   *                            在序列化时需要利用该值来读取指针指向的所有数据
   * @return 元组: (已序列化二进制数据, 在WebAssembly内存中的读指针)
   */
  def serialize(
                 structure: JObject,
                 structuresMap: mutable.HashMap[String, JArray],
                 readPointer: Int,
                 memBuffer: ByteBuffer,
                 itemCountForPointer: Int = 1
               ): (Array[Byte], Int) = {
    val varType = structure.obj(0)._2.asInstanceOf[JString].s
    val varName = structure.obj.find(field => field._1.equals("name")).get._2.asInstanceOf[JString].s

    // 处理除指针外的基本类型数据
    if (!isComplexDataType(varType) && !isPointerDataType(varType)) {
      val varSize = getSize(structure, structuresMap)
      val bytes = new Array[Byte](varSize)
      memBuffer.get(readPointer, bytes)
      val newReadPointer = readPointer + varSize
      return (bytes, newReadPointer)
    }

    // 处理指针类型数据
    if (isPointerDataType(varType)) {
      val dataType = varType.dropRight(1)
      var dataStructure: JObject = null
      if (isComplexDataType(dataType)) {
        val dataStruct = structure.obj(1)._2.asInstanceOf[JString].s
        dataStructure = Extraction.decompose(ComplexType(dataType, dataStruct, varName)).asInstanceOf[JObject]
      } else {
        dataStructure = Extraction.decompose(BasicType(dataType, varName)).asInstanceOf[JObject]
      }
      val varSize = getSize(structure, structuresMap)
      val newReadPointer = readPointer + varSize
      var dataPointer = memBuffer.slice(readPointer, varSize).order(ByteOrder.LITTLE_ENDIAN).getInt()
      var data = Array[Byte]()
      for (_ <- 1 to itemCountForPointer) {
        val res = serialize(dataStructure, structuresMap, dataPointer, memBuffer)
        data = data ++ res._1
        dataPointer = res._2
      }
      return (data, newReadPointer)
    }

    // 处理复合类型数据
    val varStruct = structure.obj(1)._2.asInstanceOf[JString].s
    val structStructure = structuresMap.get(varStruct) match {
      case Some(refStructure) => refStructure.arr
      case None => throw new Error(s"Wrong data type ${varStruct}")
    }
    var newItemCountForPointer = itemCountForPointer
    var serializedData = Array[Byte]()
    var newReadPointer = readPointer
    for (fieldStructure <- structStructure) {
      val fieldVarType = fieldStructure.asInstanceOf[JObject].obj(0)._2.asInstanceOf[JString].s
      if (isStringDataType(varStruct) || isListDataType(varStruct) || isMapDataType(varStruct)) {
        newItemCountForPointer = ByteBuffer.allocate(4)
          .order(ByteOrder.LITTLE_ENDIAN)
          .put(0, serializedData.slice(0, 4))
          .getInt()
      }
      val res = serialize(
        fieldStructure.asInstanceOf[JObject],
        structuresMap,
        getUpperPointer(
          newReadPointer,
          getAlignSize(fieldStructure.asInstanceOf[JObject], structuresMap)
        ),
        memBuffer,
        newItemCountForPointer
      )
      serializedData = serializedData ++ res._1
      newReadPointer = res._2
    }
    (serializedData, newReadPointer)
  }


  /**
   * 根据Json格式的数据结构描述，生成其对应的已序列化的默认值数据
   *
   * @param parentStructureName 当前数据结构的父数据结构的名称，如_string {int _len; char *_data;}中属性`_len`的parentStructureName为`_string`
   * @param structure           当前数据类型的Json格式描述，如{ "type": "int", "name": "_capacity" }, { "type": "struct", "struct": "_map_string_string", "name": "mss" }
   * @param structuresMap       当前已知所有复合数据结构的Json描述
   * @param capacity            针对isCL中的List及Map类型数据，需要指定容量属性`_capacity`，默认为0
   * @return 已序列化的默认值数据，字节数组
   */
  def genDefaultSerializedData(parentStructureName: String, structure: JObject, structuresMap: mutable.HashMap[String, JArray], capacity: Int = 0): Array[Byte] = {
    val dataType = structure.obj(0)._2.asInstanceOf[JString].s
    val dataName = structure.obj.find(field => field._1.equals("name")).get._2.asInstanceOf[JString].s

    // 生成除指针外的基本类型数据的默认值
    if (!isComplexDataType(dataType) && !isPointerDataType(dataType)) {
      val dataSize = getSize(structure, structuresMap)
      var bytes = new Array[Byte](dataSize)
      // 对于isCL中的List及Map类型实例，其_capacity属性默认值需不为0
      if ((isListDataType(parentStructureName) || isMapDataType(parentStructureName))
        && dataName.equals("_capacity")
      ) {
        // _capacity属性为4字节整数
        bytes = ByteBuffer.allocate(basicDataTypeSize.get("int").get)
          .order(ByteOrder.LITTLE_ENDIAN)
          .putInt(capacity)
          .array()
      }
      return bytes
    }

    // 针对指针类型数据，生成其对应非指针类型数据默认值
    if (isPointerDataType(dataType)) {
      val newDataType = dePointerDataType(dataType)
      var newDataStructure: JObject = null
      var dataStructName: String = null
      if (isComplexDataType(newDataType)) {
        dataStructName = structure.obj(1)._2.asInstanceOf[JString].s
        newDataStructure = Extraction.decompose(ComplexType(newDataType, dataStructName, dataName)).asInstanceOf[JObject]
      } else {
        newDataStructure = Extraction.decompose(BasicType(newDataType, dataName)).asInstanceOf[JObject]
      }
      var bytes = Array[Byte]()
      for (_ <- 1 to capacity) {
        val res = genDefaultSerializedData(parentStructureName, newDataStructure, structuresMap)
        bytes = bytes ++ res
      }
      return bytes
    }

    // 生成复合结构类型数据默认值
    val dataStructName = structure.obj(1)._2.asInstanceOf[JString].s
    val structStructure = structuresMap.get(dataStructName) match {
      case Some(refStructure) => refStructure.arr
      case None => throw new Error(s"Wrong data type ${dataStructName}")
    }
    val newCapacity = if (isListDataType(dataStructName) || isMapDataType(dataStructName)) 4 else capacity
    var bytes = Array[Byte]()
    for (fieldStructure <- structStructure) {
      val res = genDefaultSerializedData(dataStructName, fieldStructure.asInstanceOf[JObject], structuresMap, newCapacity)
      bytes = bytes ++ res
    }
    bytes
  }

  /**
   * 计算满足地址对齐要求的数据类型所占字节大小
   *
   * @param structure     数据类型Json描述，示例：
   *                      { "type": "int", "name": "i" } 描述该类型为整数int类型,
   *                      { "type": "char*", "name": "c" } 描述该类型为字符指针char*类型,
   *                      { "type": "struct", "struct": "_string", "name": "s" } 描述该类型为复合类型结构体,
   *                      { "type": "struct*", "struct": "_string", "name": "sp" } 描述该类型为复合类型结构体指针
   * @param structuresMap 已知复合类型数据结构Json描述，示例：
   *                      { "_string": [ { "type": "int", "name": "_len" }, { "type": "char*", "name": "_data" } ] } 描述结构体：
   *                      typedef struct {
   *                      int _len;
   *                      char *_data;
   *                      } _string;
   * @return 满足地址对齐要求的数据类型字节大小
   */
  def getSize(structure: JObject, structuresMap: mutable.HashMap[String, JArray]): Int = {
    val varType = structure.obj(0)._2.asInstanceOf[JString].s
    if (isPointerDataType(varType)) return basicDataTypeSize.get("pointer") match {
      case Some(size) => size
      case None => throw new Error(s"Wrong data type ${varType}")
    }
    if (!isComplexDataType(varType)) return basicDataTypeSize.get(varType) match {
      case Some(size) => size
      case None => throw new Error(s"Wrong data type ${varType}")
    }

    // 复合类型
    val varStruct = structure.obj(1)._2.asInstanceOf[JString].s
    val structStructure = structuresMap.get(varStruct) match {
      case Some(refStructure) => refStructure.arr
      case None => throw new Error(s"Wrong data type ${varStruct}")
    }
    var size = 0
    breakable {
      for ((fieldStructure, index) <- structStructure.zipWithIndex) {
        val fieldSize = getSize(fieldStructure.asInstanceOf[JObject], structuresMap)
        // 最后一个属性元素的填充字节大小，需要考虑整个复合类型结构体的对齐大小
        if (index == structStructure.size - 1) {
          size = getUpperPointer(size + fieldSize, getAlignSize(structure, structuresMap))
          break
        }
        // 其余属性元素的填充字节大小，需要考虑下一个元素的对齐大小
        val nextFieldStructure = structStructure(index + 1).asInstanceOf[JObject]
        val nextFieldAlignSize = getAlignSize(nextFieldStructure, structuresMap)
        size = getUpperPointer(size + fieldSize, nextFieldAlignSize)
      }
    }
    size
  }

  /**
   * 计算满足地址对齐大小要求的最小地址
   *
   * @param pointer   原指针地址
   * @param alignSize 地址对齐大小，字节数
   * @return 最小地址对齐指针地址
   */
  private def getUpperPointer(pointer: Int, alignSize: Int): Int =
    alignSize * Math.ceil(
      BigDecimal(pointer.toDouble / alignSize.toDouble).setScale(1, BigDecimal.RoundingMode.HALF_UP).toDouble
    ).toInt

  /**
   * 计算数据类型的地址对齐字节大小
   *
   * @param structure     数据类型的Json描述
   * @param structuresMap 已知的复合类型数据结构的Json描述
   * @return 地址对齐字节大小
   */
  private def getAlignSize(structure: JObject, structuresMap: mutable.HashMap[String, JArray]): Int = {
    val varType = structure.obj(0)._2.asInstanceOf[JString].s
    if (isPointerDataType(varType)) return basicDataTypeSize.get("pointer") match {
      case Some(size) => size
      case None => throw new Error(s"Wrong data type ${varType}")
    }
    if (!isComplexDataType(varType)) return basicDataTypeSize.get(varType) match {
      case Some(size) => size
      case None => throw new Error(s"Wrong data type ${varType}")
    }

    val varStruct = structure.obj(1)._2.asInstanceOf[JString].s
    val structStructure = structuresMap.get(varStruct) match {
      case Some(refStructure) => refStructure.arr
      case None => throw new Error(s"Wrong data type ${varStruct}")
    }
    var maxAlignSize = 0
    for (fieldStructure <- structStructure) {
      val fieldAlignSiz = getAlignSize(fieldStructure.asInstanceOf[JObject], structuresMap)
      if (fieldAlignSiz > maxAlignSize) maxAlignSize = fieldAlignSiz
    }
    maxAlignSize
  }

  /**
   * 对从WebAssembly内存申请空间的方法的封装
   *
   * @param malloc WebAssembly实例中定义的内存空间申请方法
   * @param size   欲申请的内存空间大小，字节数
   * @return 已申请内存空间的起始地址
   */
  def mallocWrap(malloc: Function, size: Int): Integer = {
    val pointer = malloc.apply(Integer.valueOf(size))(0).asInstanceOf[Integer]
    if (pointer == 0) throw new Error("No enough memory space in WebAssembly")
    pointer
  }

  /**
   * 从WebAssembly中读取isCL的String类型数据实例
   *
   * @param memBuffer     WebAssembly内存
   * @param stringPointer 欲读取的String数据实例的指针地址
   */
  def readString(memBuffer: ByteBuffer, stringPointer: Integer): String = {
    memBuffer.position(stringPointer)
    val length = memBuffer.getInt()
    val dataPointer = memBuffer.getInt()
    val strBytes = new Array[Byte](length)
    memBuffer.position(dataPointer)
    memBuffer.get(strBytes, 0, strBytes.length)
    new String(strBytes, UTF_8)
  }


  private final val isCLStringSize = basicDataTypeSize.get("int").get + basicDataTypeSize.get("pointer").get

  /**
   * 向WebAssembly内存中写入isCL的String类型数据实例
   * c version: struct _string { int _len; char *_data; }
   *
   * @param malloc    从WebAssembly内存申请空间的方法
   * @param memBuffer WebAssembly内存
   * @param str       欲写入的String值
   * @return 写入值所在指针地址
   */
  def writeString(malloc: Function, memBuffer: ByteBuffer, str: String): Integer = {
    val strPointer = mallocWrap(malloc, isCLStringSize)
    val strBytes = str.getBytes(UTF_8)
    memBuffer.putInt(strPointer, strBytes.length)
    val strDataPointer = mallocWrap(malloc, strBytes.length)
    memBuffer.putInt(strPointer + 4, strDataPointer)
    memBuffer.put(strDataPointer, strBytes, 0, strBytes.length)
    strPointer
  }

  /**
   * 从WebAssembly内存中读取isCL的Bool类型数据实例
   *
   * @param memBuffer   WebAssembly内存
   * @param boolPointer 欲读取的Bool数据实例的指针地址
   * @return 读取的Bool值
   */
  def readBool(memBuffer: ByteBuffer, boolPointer: Integer): Boolean = {
    if (memBuffer.get(boolPointer).toInt == 1) true else false
  }


  private final val isCLBoolSize = basicDataTypeSize.get("bool").get

  /**
   * 向WebAssembly内存中写入isCL的Bool类型数据实例
   *
   * @param malloc    从WebAssembly内存申请空间的方法
   * @param memBuffer WebAssembly内存
   * @param b         欲写入的Bool值
   * @return 写入值所在指针地址
   */
  def writeBool(malloc: Function, memBuffer: ByteBuffer, b: Boolean): Integer = {
    val boolPointer = mallocWrap(malloc, isCLBoolSize)
    val boolByte = if (b) 1.toByte else 0.toByte
    memBuffer.put(boolPointer, boolByte)
    boolPointer
  }

  /**
   * 将json格式字面值转为对应isCL编译为C数据结构已序列化后的二进制数据，
   * 主要用于将调用合约时输入的json字符串参数转为已序列化数据，方便写入WebAssembly内存
   *
   * @param jv json格式输入值
   * @param structure json格式输入值所对应的isCL编译为C的数据结构描述
   * @param structuresMap 合约中已知的所有复合类型的数据结构描述
   * @return 对应已序列化二进制数据
   */
  def json2Binary(jv: JValue, structure: JObject, structuresMap: mutable.HashMap[String, JArray]): Array[Byte] = {
    jv match {
      case JInt(num) =>
        ByteBuffer.allocate(basicDataTypeSize.get("int").get)
          .order(ByteOrder.LITTLE_ENDIAN)
          .putInt(num.toInt)
          .array()
      case JDouble(num) =>
        ByteBuffer.allocate(basicDataTypeSize.get("double").get)
          .order(ByteOrder.LITTLE_ENDIAN)
          .putDouble(num)
          .array()
      case JBool(value) =>
        ByteBuffer.allocate(basicDataTypeSize.get("bool").get)
          .order(ByteOrder.LITTLE_ENDIAN)
          .put(if (value) 1.toByte else 0.toByte)
          .array()
      case JString(s) =>
        val stringBytes = s.getBytes(UTF_8)
        val stringSize = s.length
        ByteBuffer.allocate(basicDataTypeSize.get("int").get + stringSize)
          .order(ByteOrder.LITTLE_ENDIAN)
          .putInt(stringSize)
          .put(stringBytes)
          .array()
      case JArray(arr) =>
        if (arr.groupBy(_.getClass).size > 1) {
          throw new Exception("Wrong Json array input, only support the same type items in an array")
        }
        // 对于空数组，生成其在isCL中对应的C结构体默认二进制数据
        if (arr.isEmpty) {
          return genDefaultSerializedData(null, structure, structuresMap)
        }
        val elementStructure = structuresMap(
          structure.obj.find { case (key, _) => key.equals("struct") }.get._2.asInstanceOf[JString].s
        ).arr.asInstanceOf[List[JObject]].find(
          ele => ele.obj.exists { case (_key, _value) => _key.equals("name") && _value.asInstanceOf[JString].s.equals("_data") }
        ).get
        val res = arr.map(element => json2Binary(element, elementStructure, structuresMap))
        val arrCapacityBytes = ByteBuffer.allocate(basicDataTypeSize.get("int").get)
          .order(ByteOrder.LITTLE_ENDIAN)
          .putInt(arr.length)
          .array()
        val arrLengthBytes = arrCapacityBytes
        // for _capacity, _len, _data
        Array.concat(arrCapacityBytes :: arrLengthBytes :: res : _*)
      case JObject(obj) =>
        // 对于空对象，生成其在isCL中对应的C结构体默认二进制数据
        if (obj.isEmpty) {
          return genDefaultSerializedData(null, structure, structuresMap)
        }
        val structName = structure.obj.find { case (key, _) => key.equals("struct") }.get._2.asInstanceOf[JString].s
        val propertyStructures = structuresMap(structName).arr.asInstanceOf[List[JObject]]
        // 对于isCL中的Map结构，需特殊考虑_capacity, _len, _key, _value以构建对应二进制数据
        if (isMapDataType(structName)) {
          val capacity = obj.length
          val capacityBytes = ByteBuffer.allocate(basicDataTypeSize.get("int").get)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(capacity)
            .array()
          val lenBytes = capacityBytes
          val keysBytes = List.tabulate(capacity)(i => json2Binary(
            JString(obj(i)._1),
            propertyStructures.find { case o => o.obj.exists { case (k, v) => k.equals("name") && v.asInstanceOf[JString].s.equals("_key") } }.get,
            structuresMap
          ))
          val valuesBytes = List.tabulate(capacity)(i => json2Binary(
            obj(i)._2,
            propertyStructures.find { case o => o.obj.exists { case (k, v) => k.equals("name") && v.asInstanceOf[JString].s.equals("_value") } }.get,
            structuresMap
          ))
          return Array.concat(capacityBytes :: lenBytes :: keysBytes++valuesBytes :  _*)
        }
        // 对于isCL中的一般复合类型，遍历每个property依次构建二进制数据
        val res = obj.zipWithIndex.map { case (property, index) =>
          json2Binary(property._2, propertyStructures(index), structuresMap)
        }
        Array.concat(res: _*)
      case _ => throw new Exception(s"Wrong Json input, not support the type")
    }
  }

}
