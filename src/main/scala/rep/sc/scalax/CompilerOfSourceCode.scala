package rep.sc.scalax

import collection.mutable
import rep.storage.util.pathUtil

import scala.tools.nsc.Settings
import scala.tools.nsc.Global
import scala.tools.nsc.reporters.ConsoleReporter
import java.io.{File, FileWriter}

import rep.crypto.Sha256

import scala.reflect.io.VirtualDirectory
import scala.reflect.internal.util.BatchSourceFile
import scala.reflect.runtime.currentMirror
import scala.tools.reflect.ToolBox


/**
 * 动态编译工具类的伴生对象，提供静态方法
 * @author c4w
 */
object CompilerOfSourceCode {
  //建立动态编译工具类实例
  //val contractOperationMode = SystemProfile.getContractOperationMode
  var isDebug = true
  /*contractOperationMode match {
    case 0 => isDebug = true
    case 1 => isDebug = false
  }*/
  val cp = new CompilerOfSourceCode(None, isDebug)
  /**
   * 根据传入的合约代码，以及指定的合约类唯一标识，生成合约类，并动态编译
   * @param pcode 合约代码主体部分
   * @cid 合约类唯一标识
   * @return 类定义实例
   */
  def compilef(pcode: String, cid: String): Class[_] = {
    cp.compilef(pcode, cid)
  }
}

/**
 * 负责动态编译scala合约的工具类
 *  @author c4w
 *  @constructor 允许指定编译结果的目标路径，指定是否支持合约调试
 *  @param targetDir: 动态编译并加载的class路径
 *  @param bDebug: 是否调试模式
 */
class CompilerOfSourceCode(targetDir: Option[File], bDebug: Boolean) {
  val tb = currentMirror.mkToolBox()
  //合约类名前缀
  val PRE_CLS_NAME = "SC_"
  //源文件路径
  val path_source = if (bDebug) getSourcePath else pathUtil.getPath("custom_contract")
  //目标文件路径
  val target = new VirtualDirectory("(memory)", None)/*targetDir match {
    case Some(dir) => AbstractFile.getDirectory(dir)
    case None      => new VirtualDirectory("(memory)", None)
  }*/

  //类加载器，优先从默认类加载路径加载
  //val classLoader = new AbstractFileClassLoader(target, this.getClass.getClassLoader)
  val classLoader = new ScalaForClassLoaderOfCustom(target, this.getClass.getClassLoader)
  //类定义缓冲
  val classCache = mutable.Map[String, Class[_]]()
  
   //动态编译环境设置
  val settings = new Settings()
  settings.deprecation.value = true // enable detailed deprecation warnings
  settings.unchecked.value = true // enable detailed unchecked warnings
  settings.outputDirs.setSingleOutput(target)
  settings.usejavacp.value = true
  settings.classpath.append(getSourcePath())
  settings.classpath.append(this.getClass.getClassLoader.getResource("").toString())
  
  settings.classpath.append("/Users/jiangbuyun/.ivy2/cache")
  //tb.mirror.classLoader.
  //tb.mirror.universe.runtimeMirror(getClass.getClassLoader).
  println(s"getSourcePath=${getSourcePath()}")
  println(s"getclasspath=${this.getClass.getClassLoader.getResource("")}")
  
  var path = System.getProperty("java.class.path");
 val firstIndex = path.lastIndexOf(System.getProperty("path.separator")) + 1;
 val lastIndex = path.lastIndexOf(File.separator) + 1;
 path = path.substring(firstIndex, lastIndex);
  println(s"systemclasspath=${path}")
  
  println(s"userclasspath=${System.getProperty("user.dir")}")
  
  settings.classpath.append(path)
  
  //this.getClass.getClassLoader.
   
   val outputDirName = "."
   val outputDir = new VirtualDirectory(outputDirName, None)
   settings.outputDirs.setSingleOutput(outputDir)
 
   val global = Global(settings, new ConsoleReporter(settings))
   val run = new global.Run

  /**
   * 根据运行路径获得源文件路径
   * @return 项目源文件路径
   */
  def getSourcePath():String = {
    //工程根路径
    val path_source_root = "repchain"
    //获得class路径
    val rpath = getClass.getResource("").getPath
    //获得source路径
    val p0 = rpath.indexOf(path_source_root)
    val sr = Array(rpath.substring(0, p0 + path_source_root.length()), "src", "main", "scala")
    val spath = sr.mkString(File.separator)
    spath
  }
  /**
   * 保存代码文件
   *  @param fn 文件全路径名
   *  @param code 代码内容
   */
  def saveCode(fn: String, code: String) = {
    val fout = new FileWriter(path_source + File.separator + fn + ".scala")
    fout.write(code)
    fout.close()
  }

  def createSourceFile(pcode: String, cid: String): (String, String) = {
    //去掉package声明,将class放在default路径下
    var p0 = pcode.indexOf("import")
    //第一个定位点应加强容错能力,允许空白字符
    //val pattern = "extends\\s+IContract\\s*\\{".r
    val pattern = "extends\\s+IContract\\s*\\{".r
    val p1str = pattern.findFirstIn(pcode).get
    val p1 = pcode.indexOf(p1str)
    val p2 = pcode.lastIndexOf("}")
    val p3 = pcode.lastIndexOf("class ", p1)

    //可能不存在import指令
    if (p0.equals(-1))
      p0 = p3
    if (p1.equals(-1) || p1.equals(-1) || p1.equals(-1))
      throw new RuntimeException("合约语法错误")
    val className = if (cid != null) PRE_CLS_NAME + cid else classNameForCode(pcode)

    //获取替换类名
    val oldclassname = pcode.substring(p3 + 5, p1)
    val newpcode = pcode.substring(p0)
    var mo = newpcode.replaceFirst("object\\s*" + oldclassname.trim + "\\s*\\{", "object " + className + "{")
    mo = mo.replaceFirst("class\\s*" + oldclassname.trim + "\\s*extends", "class " + className + " extends  ")
    val ncode = mo.replaceAll(oldclassname.trim + ".", className + ".")
    (className, ncode)
  }

  /**
   * 编译完整文件的代码，只替换className
   * 将code封装文件写入source路径,重新启动时无需再从区块中获得代码加载，为防止擅自修改代码，应进行代码的hash检查
   * @param pcode 待编译的合约代码
   * @param cid 合约类唯一标识,如果不指定，取代码内容的sha256结果作为标识
   * @return 编译生成的类定义
   */
  def compilef(pcode: String, cid: String): Class[_] = {
    val midifiedCode = createSourceFile(pcode: String, cid: String)
    var cl: Option[Class[_]] = None
    try{
      cl = Some(Class.forName(midifiedCode._1))
    }catch {
      case e:Throwable =>     
        cl = findClass(midifiedCode._1)          
    } 
    //if(cl!=None)
    //    return cl.get  
    
   if(path_source!=null)
      saveCode(midifiedCode._1,midifiedCode._2)  
        
    var cls = compile(midifiedCode._1,midifiedCode._2)

    classCache(midifiedCode._1) = cls
    cls
  }
  
  def compile(className:String, ncode:String): Class[_] = {
   val inputFileName = className+".scala"
   val inputFile = new BatchSourceFile(inputFileName, ncode)

   println(s"input file=${inputFile.path}")
   println(s"setting output dir =${settings.outputDirs.getSingleOutput.get.path}")
   
   val parser = new global.syntaxAnalyzer.SourceFileParser(inputFile)
   val tree = parser.parse
   println(tree)

   run.compileSources(List(inputFile))

   val outputFileName = className+".class"
   val outputFile =outputDir.lookupName(outputFileName, false)

   val classBytes = outputFile.toByteArray
   println("Number of bytes in output file: " + classBytes.length)
   
   classLoader.LoadClassForByte(className, classBytes)
  }

  /**
   * 尝试加载类定义
   * 	@param className 类名称
   *  @return 类定义
   */
  def findClass(className: String): Option[Class[_]] = {
    synchronized {
      classCache.get(className).orElse {
        try {
          val cls = classLoader.loadClass(className)
          classCache(className) = cls
          Some(cls)
        } catch {
          case e: ClassNotFoundException => None
        }
      }
    }
  }

  /**
   * 获得合约代码的类名
   * 	@param code 合约代码
   *  @return 类名字符串
   */
  protected def classNameForCode(code: String): String = {
    PRE_CLS_NAME + Sha256.hashstr(code)
  }

}