package rep.accumulator.verkle.util

import java.util

object verkleTool {
  def getIndex(b:Byte):Int={
    b.toInt+128
  }

  def getIndex(bs:Array[Byte]):Array[Int]={
    var r : Array[Int] = null
    if(bs != null){
      r = new Array[Int](bs.length)
      for(i<-0 to r.length-1){
        r(i) = bs(i).toInt + 128
      }
    }
    r
  }

  def getKey(src:Array[Int]):String={
    val r : StringBuffer = new StringBuffer()
    if(src == null){
      "null"
    }else{
      for (i <- 0 to src.length - 1) {
        if (i == 0) {
          r.append(src(i))
        } else {
          r.append("-").append(src(i))
        }
      }
      r.toString
    }
  }

  def comparePath(prefix:Array[Int],path:Array[Int]):Boolean={
    if(prefix.length < path.length){
      util.Arrays.equals(prefix,path.slice(0,prefix.length))
    }else{
      false
    }

  }
}
