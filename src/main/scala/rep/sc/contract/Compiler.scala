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

package rep.sc.contract


import scala.tools.nsc.{Global, Settings}
import scala.reflect.internal.util.BatchSourceFile
import tools.nsc.io.{AbstractFile, VirtualDirectory}
import scala.reflect.internal.util.AbstractFileClassLoader
import java.security.MessageDigest
import java.math.BigInteger

import collection.mutable
import java.io._

import org.json4s._
import org.json4s.jackson.JsonMethods._
import rep.crypto.ShaDigest
import rep.app.conf.SystemProfile

import scala.reflect.runtime.currentMirror
import scala.tools.reflect.ToolBox
import rep.storage.util.pathUtil

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
}

/** 负责动态编译scala合约的工具类
 *  @author c4w
 *  @constructor 允许指定编译结果的目标路径，指定是否支持合约调试
 *  @param targetDir: 动态编译并加载的class路径
 *  @param bDebug: 是否调试模式
 */
class Compiler(targetDir: Option[File], bDebug:Boolean) {
  //合约类名前缀
  val PRE_CLS_NAME = "sha"
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
    //工程根路径
    val path_source_root = "repchain"       
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
   //去掉package声明,将class放在default路径下
    var p0 = pcode.indexOf("import")
     //第一个定位点应加强容错能力,允许空白字符
    val pattern = "extends\\s+IContract\\s*\\{".r
    val p1str = pattern.findFirstIn(pcode).get
    val p1 = pcode.indexOf(p1str)
    val p2 = pcode.lastIndexOf("}")
    val p3 = pcode.lastIndexOf("class ",p1)
    
    //可能不存在import指令
    if(p0.equals(-1)) 
      p0 = p3
    if(p1.equals(-1) || p1.equals(-1) || p1.equals(-1))
      throw new RuntimeException("合约语法错误")
    val className = if(cid!=null) PRE_CLS_NAME+cid else classNameForCode(pcode)
    try{
      val cl = Class.forName(className)
      cl      
    }catch {
      case e:Throwable =>     
        findClass(className).getOrElse {
        //替换类名为 hash256
        val ncode = pcode.substring(p0,p3) + "class "+className+ " "+pcode.substring(p1,p2+1)
        //+"\nscala.reflect.classTag[ContractAssets2].runtimeClass"
        if(path_source!=null)
          saveCode(className,ncode)
      
        val cls =   tb.compile(tb.parse(ncode +"\nscala.reflect.classTag["
          +className
          +"].runtimeClass"))().asInstanceOf[Class[_]]
        classCache(className) = cls
        cls
      }

    }
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
    PRE_CLS_NAME + ShaDigest.hashstr(code)
  }

}