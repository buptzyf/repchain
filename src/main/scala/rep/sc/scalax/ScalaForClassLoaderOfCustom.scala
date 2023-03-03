package rep.sc.scalax

import scala.reflect.internal.util.AbstractFileClassLoader
import scala.reflect.io.AbstractFile

class ScalaForClassLoaderOfCustom(override val root: AbstractFile, parent: ClassLoader) extends AbstractFileClassLoader( root, parent){
  def LoadClassForByte(name:String,bytes:Array[Byte]):Class[_]={
    defineClass(name, bytes, 0, bytes.length, protectionDomain)
  }
}