package rep.network.consensus.asyncconsensus.protocol.msgcenter

import akka.actor.ActorRef

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-03-13
 * @category	包装公共的广播通讯。
 */

object Broadcaster{
  def buildDestPath(nodeAddr:String,nodePath:String,nodeName:String,moduleName:String): String ={
    println(s"broadcast addr=${nodeAddr+nodePath+"/"+nodeName+"-"+moduleName}")
    nodeAddr+nodePath+"/"+nodeName+"-"+moduleName
  }

  def buildDestPathInScheam(nodeAddr:String,nodePath:String,nodeName:String,moduleName:String): String ={
    val path = nodePath.replaceAll("\\{nodeName\\}",nodeName)
    val name = moduleName.replaceAll("\\{nodeName\\}",nodeName)
    println(s"broadcast addr=${nodeAddr+path+"/"+name}")
    nodeAddr+path+"/"+name
  }

  def buildInstanceName(nodeName:String,moduleName:String):String={
    nodeName+"-"+moduleName
  }

  def splitModuleName(moduleName:String):String={
    val token = moduleName.split("-")
    if(token != null && token.length>=2){
      token(1)
    }else{
      moduleName
    }
  }

  def getModulePath(actor:ActorRef):String={
    if(actor != null){
      val path = akka.serialization.Serialization.serializedActorPath(actor)
      val pos1 = path.indexOf("/user")
      val pos2 = path.lastIndexOf("/")
      if(pos1 > 0 && pos2 > 0 && pos2 > pos1){
        path.substring(pos1,pos2)
      }else{
        ""
      }
    }else{
      ""
    }
  }

  def getModuleNodeName(moduleName:String):String={
    val token = moduleName.split("_")
    if(token != null && token.length>=2){
      token(0)
    }else{
      moduleName
    }
  }
}

class Broadcaster(sender: IMessageOfSender,nodes:Array[(String,String)]) {

  def broadcast(path:String,moduleName:String,msg:Any): Unit ={
    this.nodes.foreach(node=>{
      this.sender.sendMsg(Broadcaster.buildDestPath(node._2,path,node._1,moduleName),msg)
    })
  }

  def broadcastInSchema(pathSchema:String,moduleScheam:String,msg:Any): Unit ={
    this.nodes.foreach(node=>{
      this.sender.sendMsg(Broadcaster.buildDestPathInScheam(node._2,pathSchema,node._1,moduleScheam),msg)
    })
  }

  def broadcastExceptSelf(path:String,nodeName:String,moduleName:String,msg:Any): Unit ={
    this.nodes.foreach(node=>{
      if(node._1 != nodeName){
        this.sender.sendMsg(Broadcaster.buildDestPath(node._2,path,node._1,moduleName),msg)
      }
    })
  }

  def broadcastExceptSelfInSchema(pathSchema:String,nodeName:String,moduleSchema:String,msg:Any): Unit ={
    this.nodes.foreach(node=>{
      if(node._1 != nodeName){
        this.sender.sendMsg(Broadcaster.buildDestPathInScheam(node._2,pathSchema,node._1,moduleSchema),msg)
      }
    })
  }

  def broadcastSpeciaNodeInSchema(pathSchema:String,nodeName:String,moduleSchema:String,msg:Any): Unit ={
    this.nodes.foreach(node=>{
      if(node._1 == nodeName){
        this.sender.sendMsg(Broadcaster.buildDestPathInScheam(node._2,pathSchema,node._1,moduleSchema),msg)
      }
    })
  }

  def broadcastResultToSpecialNode(recver:Any,msg:Any):Unit={
    this.sender.sendMsgToObject(recver,msg)
  }

  def broadcastForEach(path:String,moduleName:String,msgs:Array[Any]): Unit ={
    val len = math.min(nodes.length,msgs.length)
    var i = 0
    for(i <- 0 to len-1){
      val node = nodes(i)
      this.sender.sendMsg(Broadcaster.buildDestPath(node._2,path,node._1,moduleName),msgs(i))
    }
  }

  def broadcastForEachInSchema(path:String,moduleName:String,msgs:Array[Any]): Unit ={
    val len = math.min(nodes.length,msgs.length)
    var i = 0
    for(i <- 0 to len-1){
      val node = nodes(i)
      this.sender.sendMsg(Broadcaster.buildDestPathInScheam(node._2,path,node._1,moduleName),msgs(i))
    }
  }
}
