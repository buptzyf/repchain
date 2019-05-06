package rep.storage.test

import rep.sc.scalax.CompilerOfSourceCode
import rep.sc.scalax.IContract

object testcompilerofSource extends App {
  var cobj: IContract = null
  val s1 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/ContractCert1.scala")
  val pcode = try s1.mkString finally s1.close()
  val clazz = CompilerOfSourceCode.compilef(pcode, "accoutname_1")  
    try{
      cobj = clazz.getConstructor().newInstance().asInstanceOf[IContract]
    }catch{
      case e:Exception =>cobj = clazz.newInstance().asInstanceOf[IContract]
    }
}