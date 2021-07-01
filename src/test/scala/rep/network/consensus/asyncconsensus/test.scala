package rep.network.consensus.asyncconsensus

object test extends  App {
  var str = "{nodeName}"+"\\"+"sdfs"+"\\"+"{nodeName}"+"\\"+"lkjlkjl"
  println(str.replaceAll("\\{nodeName\\}","node1"))

}
