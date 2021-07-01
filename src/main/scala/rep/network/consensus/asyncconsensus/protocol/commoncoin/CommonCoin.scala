package rep.network.consensus.asyncconsensus.protocol.commoncoin


import java.util.concurrent.ConcurrentHashMap

import akka.actor.ActorRef
import rep.network.consensus.asyncconsensus.protocol.msgcenter.Broadcaster
import rep.protos.peer.{ShareSignOfCommonCoin, StartCommonCoin}
import scala.collection.JavaConverters._
/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-02-13
 * @category	分布式掷币协议体的主要实现。
 */
/*object CommonCoin{
  case class StartCommonCoin(val source:String, val singleBit:Boolean)
  case class ShareSignOfCommonCoin(val source:String,sign:String,nodeName:String)
  case class ResultOfCommonCoin(val source:String,result:String)
}*/

class CommonCoin(val nodeName:String, val pathSchema:String,moduleSchema:String, broadcaster: Broadcaster,singleBit:Boolean,caller:ActorRef){//},val caller: ActorRef) {
  private   var coins = new ConcurrentHashMap[String, Coin] asScala
  private var moduleName = moduleSchema.replaceAll("\\{nodeName\\}",this.nodeName)

  def startup(msg: StartCommonCoin):Unit={
    println(s"common coin,startup nodeName=${this.nodeName},msg:round=${msg.round},source=${msg.source},single=${msg.singleBit}")

    val coin = this.getCoin(msg.source,msg.round)
    val data = coin.startup(msg)
    //this.broadcaster.broadcast(this.path,this.moduleName,new ShareSignOfCommonCoin(this.source,sign,this.nodeName))
    this.broadcaster.broadcastExceptSelfInSchema(this.pathSchema,this.nodeName,this.moduleSchema,data)
  }

  def handler(msg:ShareSignOfCommonCoin):Unit={
    println(s"common coin,ShareSignOfCommonCoin nodeName=${this.nodeName},msg:round=${msg.round},source=${msg.source},single=${msg.sign.toString}")
    val coin = this.getCoin(msg.source,msg.round)
    val data = coin.handler(msg)
    if(data != null){
      //this.broadcaster.broadcastResultToSpecialNode(caller, data)
      println(s"common coin,result nodeName=${this.nodeName},msg:round=${data.round},source=${data.source},result=${data.result},caller=${this.caller.path}")
      caller ! data
    }
  }

  private def getCoin(source:String,round:String):Coin={
    var coin : Coin = null
      if(this.coins.contains(source)){
        coin = this.coins(source)
      }else{

        coin = new Coin(nodeName,source,round,this.singleBit)
        this.coins += source -> coin
      }
    coin
  }

}
