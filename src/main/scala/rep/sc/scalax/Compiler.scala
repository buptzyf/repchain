/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BA SIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package rep.sc.scalax


import scala.tools.nsc.{Global, Settings}
import tools.nsc.io.{VirtualDirectory, AbstractFile}
import scala.reflect.internal.util.AbstractFileClassLoader
import collection.mutable
import java.io._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import rep.crypto.Sha256
import rep.app.conf.SystemProfile
import scala.reflect.runtime.currentMirror
import scala.tools.reflect.ToolBox
import rep.storage.util.pathUtil
import scala.reflect.io.Path.jfile2path

/**
 * 动态编译工具类的伴生对象，提供静态方法
 * @author c4w
 */
object Compiler{
  //建立动态编译工具类实例
  val contractOperationMode = SystemProfile.getContractOperationMode
  var isDebug = true
  contractOperationMode match{
    case 0 => isDebug = true
    case 1 => isDebug = false
  }
  val cp = new Compiler(None, isDebug)
  /** 根据传入的合约代码，以及指定的合约类唯一标识，生成合约类，并动态编译
   * @param pcode 合约代码主体部分
   * @cid 合约类唯一标识
   * @return 类定义实例
   */
  def compilef(pcode: String, cid: String): Class[_]= {
    cp.compilef(pcode, cid)
  }

  /**
   * 为case class加cid前缀,避免合约间重名
   */
  def prefixCaseClass(code:String, pre:String) : String={
    val ptn = """case\s+class\s+(\S+)\s*\(""".r
    var rc = code
    for(ptn(cn) <- ptn.findAllIn(code)){
       val pcn = pre+cn
       //替换定义
       rc = rc.replaceFirst("""case\s+class\s+"""+cn+"""\s*\(""".r,  "case class "+pcn+"(")
       //替换json造型
       rc = rc.replaceAll("""\[\s*"""+cn+"""\s*\]""".r,  "["+pcn+"]")
       //替换类型声明
       rc = rc.replaceAll(""":\s*"""+cn+"""""".r,  ":"+pcn)
       //替换构造实例
        rc = rc.replaceAll("""=(\s*|\s*new\s*)"""+cn+"""\s*\(""".r,  "= new "+pcn+"(")
    }
    rc
  }
  
  def prefixCode(code:String, cn:String) :String = {
    prefixCaseClass(prefixClass(code,cn),cn)
  }
  /**
   * 为class加cid前缀，避免合约间重名,移除package声明,增加classTag声明
   */
  def prefixClass(code:String, cn:String) :String ={
     //val pattern = """class\s+(S+)\s+extends\s+IContract\s*\{"""
     //移除package声明——由于reflect不支持
     var c = code
     c = c.replaceFirst("""\s*package\s+\S+""", "")
     //替换类名
     c = c.replaceFirst("""class\s+(\S+)\s+extends\s+IContract\s*\{""", "class "+cn + " extends IContract{")
     //增加返回classTag
     //c +=  "\nscala.reflect.classTag[" + cn  +"].runtimeClass"
     c
  }
}

/** 负责动态编译scala合约的工具类
 *  @author c4w
 *  @constructor 允许指定编译结果的目标路径，指定是否支持合约调试
 *  @param targetDir: 动态编译并加载的class路径
 *  @param bDebug: 是否调试模式
 */
class Compiler(targetDir: Option[File], bDebug:Boolean) {
  //合约类名前缀
  val PRE_CLS_NAME = "SC_"
  //反射工具对象
  val tb = currentMirror.mkToolBox()
  //源文件路径
  val path_source = if(bDebug) getSourcePath else pathUtil.getPath("custom_contract")
  //目标文件路径
  val target = targetDir match {
    case Some(dir) => AbstractFile.getDirectory(dir)
    case None => new VirtualDirectory("(memory)", None)
  }
  //类定义缓冲
  val classCache = mutable.Map[String, Class[_]]()
  //动态编译环境设置
  private val settings = new Settings()
  settings.deprecation.value = true // enable detailed deprecation warnings
  settings.unchecked.value = true // enable detailed unchecked warnings
  settings.outputDirs.setSingleOutput(target)
  settings.usejavacp.value = true
  settings.classpath.append(getSourcePath())

  private val global = new Global(settings)
  private lazy val run = new global.Run
  //类加载器，优先从默认类加载路径加载
  val classLoader = new AbstractFileClassLoader(target, this.getClass.getClassLoader)

  /** 根据运行路径获得源文件路径
   * @return 项目源文件路径
   */
  def getSourcePath()={
    //工程根路径,自动获取项目名称
    val projectName = System.getProperty("user.dir");
    val path_source_root = projectName.substring(projectName.lastIndexOf(File.separatorChar) + 1, projectName.length)
    //val path_source_root = "repchain"

    //获得class路径
    val rpath = getClass.getResource("").getPath
    //获得source路径
    val p0 = rpath.indexOf(path_source_root)
    val sr = Array(rpath.substring(0,p0+ path_source_root.length()),"src","main","scala")
    val spath = sr.mkString(File.separator)
    spath
  }
  /** 保存代码文件
   *  @param fn 文件全路径名
   *  @param code 代码内容
   */
  def saveCode(fn:String, code:String)={
    val fout = new FileWriter(path_source+File.separator+fn+".scala") 
    fout.write(code)
    fout.close()   
  }

  /**
   * 编译完整文件的代码，只替换className
   * 将code封装文件写入source路径,重新启动时无需再从区块中获得代码加载，为防止擅自修改代码，应进行代码的hash检查
   * @param pcode 待编译的合约代码
   * @param cid 合约类唯一标识,如果不指定，取代码内容的sha256结果作为标识
   * @return 编译生成的类定义
   */
  def compilef(pcode: String, cid: String): Class[_]= {
    val className = if(cid!=null) PRE_CLS_NAME+cid else classNameForCode(pcode)
    var cl: Option[Class[_]] = None
    try{
      cl = Some(Class.forName(className))
    }catch {
      case e:Throwable =>     
        cl = findClass(className)          
    } 
    if(cl!=None)
        return cl.get      
    //获取替换类名
    val ncode = Compiler.prefixCode(pcode, className)
    if(path_source!=null)
      saveCode(className,ncode)  
    val cls = tb.compile(tb.parse(ncode +"\nscala.reflect.classTag["
      +className
      +"].runtimeClass"))().asInstanceOf[Class[_]]
    classCache(className) = cls
    cls  
  }

/** 尝试加载类定义
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

  /** 获得合约代码的类名
   * 	@param code 合约代码
   *  @return 类名字符串
   */
  protected def classNameForCode(code: String): String = {
    PRE_CLS_NAME + Sha256.hashstr(code)
  }

}