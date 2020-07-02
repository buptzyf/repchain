package rep.sc.compile

import com.fasterxml.jackson.module.scala.deser.overrides.LazyList
import rep.protos.peer.ChaincodeId
import rep.sc.scalax.{Compiler, IContract}

import scala.runtime.{LazyBoolean, LazyInt, LazyLong, LazyRef, LazyShort}

object testCompile extends App {
  /*val ju = scala.reflect.runtime.universe
  //得到一个JavaMirror,用于反射Person.class(获取对应的Mirrors,这里是运行时的)
  val mirror = ju.runtimeMirror(getClass.getClassLoader)*/

  var cobj: IContract = null
  val s2 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/MultiSignErr.scala","UTF-8")
  //val s2 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/ContractAssetsTPL.scala","UTF-8")
  val c2 = try s2.mkString finally s2.close()

  val code = c2
  val cid = "MultiSignContractOk_1"
  val clazz = Compiler.compilef(code, cid,false)

  try{

    /*val classPerson = ju.typeOf[clazz.type].typeSymbol.asClass
    //用Mirrors去reflect对应的类,返回一个Mirrors的实例,而该Mirrors装载着对应类的信息
    val classMirror = mirror.reflectClass(classPerson)
    //得到构造器方法
    val constructor = ju.typeOf[clazz.type].decl(ju.termNames.CONSTRUCTOR).asMethod
    val methodMirror = classMirror.reflectConstructor(constructor)
    val obj = methodMirror.apply()*/



    val constructors = clazz.getConstructors
    val tmp = clazz.getDeclaredConstructors
    if(constructors != null && constructors.length > 0){
      val constructor = constructors(0)
      if(constructor.getParameterCount > 0){
        val param_types = constructor.getParameterTypes
        var isright = true
        var param = new Array[Object](param_types.length)
        var i = 0
        param_types.foreach(f=>{
          println(s"${f.toString}\t")
          if(f.getName.equalsIgnoreCase("scala.runtime.LazyRef")) {
            param(i) = new scala.runtime.LazyRef[Any]
          }else if(f.getName.equalsIgnoreCase("scala.runtime.LazyBoolean")) {
            param(i) = new scala.runtime.LazyBoolean
          }else if(f.getName.equalsIgnoreCase("scala.runtime.LazyInt")) {
            param(i) = new scala.runtime.LazyInt
          }else if(f.getName.equalsIgnoreCase("scala.runtime.LazyLong")) {
            param(i) = new scala.runtime.LazyLong
          }else if(f.getName.equalsIgnoreCase("scala.runtime.LazyShort")) {
            param(i) = new scala.runtime.LazyShort
          }else if(f.getName.equalsIgnoreCase("scala.runtime.LazyUnit")) {
            param(i) = new scala.runtime.LazyUnit
          }else if(f.getName.equalsIgnoreCase("scala.runtime.LazyDouble")) {
            param(i) = new scala.runtime.LazyDouble
          }else if(f.getName.equalsIgnoreCase("scala.runtime.LazyChar")) {
            param(i) = new scala.runtime.LazyChar
          }else if(f.getName.equalsIgnoreCase("scala.runtime.LazyFloat")) {
            param(i) = new scala.runtime.LazyFloat
          }else if(f.getName.equalsIgnoreCase("scala.runtime.LazyByte")) {
            param(i) = new scala.runtime.LazyByte
          }
          i += 1
        })
        cobj = constructor.newInstance(new LazyRef[Any]).asInstanceOf[IContract]
      }else{
        cobj = clazz.getConstructor().newInstance().asInstanceOf[IContract]
      }
    }
  }catch{
    case e:Exception =>{
      e.printStackTrace()
      cobj = clazz.newInstance().asInstanceOf[IContract]

    }
  }

  println("ok")

}
