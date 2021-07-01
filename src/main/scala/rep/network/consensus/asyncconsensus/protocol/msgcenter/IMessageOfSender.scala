package rep.network.consensus.asyncconsensus.protocol.msgcenter

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-03-13
 * @category	广播接口。
 */

trait IMessageOfSender {
  def sendMsg(addr:String,MsgBody:Any): Unit
  def sendMsgToObject(recver:Any,MsgBody:Any):Unit
}
