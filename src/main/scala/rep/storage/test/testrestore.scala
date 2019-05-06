/*
 * Copyright  2019 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
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

package rep.storage.test

import rep.storage._
import rep.protos.peer._
import rep.utils._

import scala.tools.nsc.Settings
import scala.tools.nsc.Global
import scala.tools.nsc.reporters.ConsoleReporter
import scala.io.Source
import scala.reflect.io.AbstractFile
import scala.reflect.internal.util.SourceFile
import java.nio.file.Files
import java.nio.file.Paths
import scala.reflect.io.VirtualDirectory


import scala.io.Source
import scala.reflect.internal.util.SourceFile
import scala.reflect.internal.util.BatchSourceFile
import scala.reflect.io.AbstractFile
import java.io.File

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

import scala.reflect.runtime.universe

import scala.tools.reflect.ToolBox
import rep.storage.util.pathUtil
import java.io._

import java.lang.reflect.Constructor
/**
 * @author jiangbuyun
 * @version	0.7
 * @since	2017-09-28
 */
object testrestore extends Object {

  def getSourcePath(packagedir: String) = {
    //工程根路径
    val path_source_root = "repchain"
    //获得class路径
    val rpath = getClass.getResource("").getPath
    //获得source路径
    val p0 = rpath.indexOf(path_source_root)
    val sr = Array(rpath.substring(0, p0 + path_source_root.length()), "src", "main", "scala", packagedir)
    val spath = sr.mkString(File.separator)
    pathUtil.MkdirAll(spath)
    spath
  }
  
  def getSourcePath() = {
    //工程根路径
    val path_source_root = "repchain"
    //获得class路径
    val rpath = getClass.getResource("").getPath
    //获得source路径
    val p0 = rpath.indexOf(path_source_root)
    val sr = Array(rpath.substring(0, p0 + path_source_root.length()), "src", "main", "scala")
    val spath = sr.mkString(File.separator)
    //pathUtil.MkdirAll(spath)
    spath
  }

  def createSourceFile: (String,String) = {
    val s1 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/ContractCert1.scala")
    val pcode = try s1.mkString finally s1.close()

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
    val className = "SC_ACCountName_1"

    //获取替换类名
    val oldclassname = pcode.substring(p3 + 5, p1)
    var newpcode = pcode.substring(p0)
    val packagename = className + "_package"
    newpcode = "package " + className + "_package" + "\n" + newpcode
    var mo = newpcode.replaceFirst("object\\s*" + oldclassname.trim + "\\s*\\{", "object " + className + "{")
    mo = mo.replaceFirst("class\\s*" + oldclassname.trim + "\\s*extends", "class " + className + " extends  ")
    var ncode = mo.replaceAll(oldclassname.trim + ".", className + ".")
    ncode = "package " + packagename + "\n" + ncode
    println(ncode)
    saveCode(getSourcePath(packagename), className, ncode)
    (className,ncode)
  }

  def saveCode(path_source: String, fn: String, code: String) = {
    val fout = new FileWriter(path_source + File.separator + fn + ".scala")
    fout.write(code)
    fout.close()
  }

  def compileSource1(targetDir: Option[File], bDebug:Boolean) = {
    val tb = universe.runtimeMirror(getClass.getClassLoader).mkToolBox()
    val target = new VirtualDirectory("(memory)", None)
    
  //类定义缓冲
  val classCache = mutable.Map[String, Class[_]]()
  //动态编译环境设置
   val settings = new Settings()
  settings.deprecation.value = true // enable detailed deprecation warnings
  settings.unchecked.value = true // enable detailed unchecked warnings
  settings.outputDirs.setSingleOutput(target)
  settings.usejavacp.value = true
  settings.classpath.append(getSourcePath())
  
  val global = new Global(settings)
  lazy val run = new global.Run
  //类加载器，优先从默认类加载路径加载
  val classLoader = new AbstractFileClassLoader(target, this.getClass.getClassLoader)
    val classDef = tb.parse {
      """
      |import rep.sc.scalax.{ ContractContext, ContractException, IContract }
      |import rep.protos.peer.ActionResult
      |
      |//case class mycase1(s:String,i:Int)
      |  case class CertStatus(credit_code: String, name: String, status: Boolean)
      |class MyParser1 extends IContract{
      |  
      |  override def init(ctx: ContractContext)={
      |    val c = ctx
      |  }
      |  override def onAction(ctx: ContractContext ,action:String, sdata:String):ActionResult={
      |    null
      |  }
      |  
      |  def tomycase(credit_code: String, name: String, status: Boolean):CertStatus={
      |     CertStatus(credit_code, name, status)
      |     
      |  }
      |  
      |  def mystr(ss:CertStatus)={
      |  //println(ss.s)
      |  }
      |  

      |}
      |
      |scala.reflect.classTag[MyParser1].runtimeClass
    """.stripMargin
    }
    val clazz = tb.compile(classDef).apply().asInstanceOf[Class[_]]
    val constructor =  clazz.getDeclaredConstructors()(0).asInstanceOf[Constructor[_]]   
    constructor.setAccessible(true)
    
    val con = clazz.getConstructors

    val instance = clazz.getConstructor().newInstance()
    println(clazz)
  }

  
  
  
  /*def compileFile(){
    val className = createSourceFile
   val inputFileName = className._1 +".scala"
   val inputFile = new BatchSourceFile(inputFileName)

   val settings = new Settings
   val outputDirName = "myOutputDir"
   val outputDir = new VirtualDirectory(outputDirName, None)
   settings.outputDirs.setSingleOutput(outputDir)
   val global = Global(settings, new ConsoleReporter(settings))

   val run = new global.Run
   val parser = new global.syntaxAnalyzer.SourceFileParser(inputFile)
   val tree = parser.parse
   println(tree)

   run.compileSources(List(inputFile))

   val outputFileName = "HelloWorld.class"
   val outputFile =
     outputDir.lookupName("a", true).lookupName("b", true).lookupName("c",
true).lookupName(outputFileName, false)

   val classBytes = outputFile.toByteArray
   println("Number of bytes in output file: " + classBytes.length)
  }*/

  def main(args: Array[String]): Unit = {

    compileSource1(None,false)

  }

}



/*val sourceString = "package a.b.c\n class HelloWorld { def hello : Unit = println(\"Hello World!\") }"
   val inputFileName = "HelloWorld.scala"
   val inputFile = new BatchSourceFile(inputFileName, sourceString)

   val settings = new Settings
   val outputDirName = "myOutputDir"
   val outputDir = new VirtualDirectory(outputDirName, None)
   settings.outputDirs.setSingleOutput(outputDir)
   val global = Global(settings, new ConsoleReporter(settings))

   val run = new global.Run
   val parser = new global.syntaxAnalyzer.SourceFileParser(inputFile)
   val tree = parser.parse
   println(tree)

   run.compileSources(List(inputFile))

   val outputFileName = "HelloWorld.class"
   val outputFile =
     outputDir.lookupName("a", true).lookupName("b", true).lookupName("c",
true).lookupName(outputFileName, false)

   val classBytes = outputFile.toByteArray
   println("Number of bytes in output file: " + classBytes.length)*/

 //val s1 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/ContractCert1.scala")
    //val pcode = try s1.mkString finally s1.close()

    //var p0 = pcode.indexOf("import")
    //第一个定位点应加强容错能力,允许空白字符
    //val pattern = "extends\\s+IContract\\s*\\{".r
    /* val pattern = "extends\\s+IContract\\s*\\{".r
    val p1str = pattern.findFirstIn(pcode).get
    val p1 = pcode.indexOf(p1str)
    val p2 = pcode.lastIndexOf("}")
    val p3 = pcode.lastIndexOf("class ",p1)

    //可能不存在import指令
    if(p0.equals(-1))
      p0 = p3
    if(p1.equals(-1) || p1.equals(-1) || p1.equals(-1))
      throw new RuntimeException("合约语法错误")
    val className = "SC_ACCountName_1"

    //获取替换类名
    val oldclassname = pcode.substring(p3+5,p1)
    val newpcode = pcode.substring(p0)
    var mo = newpcode.replaceFirst("object\\s*"+oldclassname.trim+"\\s*\\{", "object "+className +"{")
    mo =mo.replaceFirst("class\\s*"+oldclassname.trim+"\\s*extends", "class "+className +" extends  ")
    val ncode =mo.replaceAll(oldclassname.trim+".", className +".")
    println(ncode)
    val classdef1 = tb.parse {
    s"""
      ${ncode} \n
      |
      |scala.reflect.classTag[${className}].runtimeClass \n

    """.stripMargin
  }

  var cls =   tb.compile(tb.parse(ncode +"\nscala.reflect.classTag["
      +className+".CertStatus"
      +"$].runtimeClass")).apply().asInstanceOf[Class[_]]
  println(cls.getConstructor().newInstance())

  var cls1 =   tb.compile(tb.parse(ncode +"\nscala.reflect.classTag["
      +className+".CertInfo"
      +"$].runtimeClass")).apply().asInstanceOf[Class[_]]
  println(cls1.getConstructor().newInstance())

  var cls2 =   tb.compile(tb.parse(ncode +"\nscala.reflect.classTag["
      +className
      +"$].runtimeClass")).apply().asInstanceOf[Class[_]]
  println(cls2.getConstructor().newInstance())*/


  /* def main(args: Array[String]): Unit = {
   val s2 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/ContractCert.scala")
    val pcode = try s2.mkString finally  s2.close()
    var p0 = pcode.indexOf("import")   
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
    
    val oldclassname = pcode.substring(p3+5,p1)
    
    val newname = " chaincode_1"
    
    
    
    var mo = pcode.replaceAll("object\\s*"+oldclassname.trim+"\\s*\\{", "object "+newname +"{")
    mo =mo.replaceAll("class\\s*"+oldclassname.trim+"\\s*extends", "class "+newname +" extends ")
    mo =mo.replaceAll(oldclassname.trim+".", newname +".")
    
    
    println(mo)
    
		}
}*/