package rep.sc
import rep.protos.peer.{Transaction,OperLog}
import rep.storage.ImpDataPreload
import akka.actor.ActorSystem
import rep.network.tools.PeerExtension
import scala.collection.mutable.{ListBuffer,ArrayBuffer} 
import java.io._
import  _root_.com.google.protobuf.ByteString 

object ShimRecord{
  type Key = String  
  type Value = Array[Byte]
  import rep.storage.IdxPrefix._
  val ERR_CERT_EXIST = "证书已存在"
  val PRE_CERT_INFO = "CERT_INFO_"
  val PRE_CERT = "CERT_"
  val NOT_PERR_CERT = "非节点证书"
  
  def serialise(value: Any): Array[Byte] = {
    val stream: ByteArrayOutputStream = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(stream)
    oos.writeObject(value)
    oos.close()
    stream.toByteArray
  }

  def deserialise(bytes: Array[Byte]): Any = {
    val ois = new ObjectInputStream(new ByteArrayInputStream(bytes))
    val value = ois.readObject
    ois.close()
    value
  }
  
}

class Invoker(shim: Shim){
  var count = 0
  val command_list: ArrayBuffer[BaseCommand] = ArrayBuffer()
  
  def pushCommand(cmd: BaseCommand):Any = {
    cmd.pos = count
    val exec_result: Option[Any] = cmd.execute()
    val r =exec_result.getOrElse(null) //这个无所谓，反正是最后返回给Shim中方法用的
    
    command_list.append(cmd) //为啥我这里只能append，没有addOne的方法？
                             //另外cmd序列化成什么样子
    
   
    val second: ByteString = cmd.result match{
      case None => ByteString.EMPTY
      case Some(rs) => rs match{
        case rs: Shim.Value => ByteString.copyFrom(rs)
        case default => ByteString.copyFrom(ShimRecord.serialise(rs)) 
      }
    }
    val sresult: String = cmd.result match{
      case None => ""
      case Some(rs) => rs match{
        case rs: String => rs
        case rs: Int => rs.toString()
        case default => ""
      }
    }
    val args = "args: "+ cmd.args.toString()+"|method: "+cmd.method + "|result: " + sresult
    val cmd_log = OperLog(args, second ,ByteString.EMPTY)
    shim.ol.append(cmd_log)
    count += 1
    return r
  }

  //导出为字节流
  def serialize(): Array[Byte] = {
    val bos = new ByteArrayOutputStream()
    val os = new ObjectOutputStream(bos)
    os.writeObject(command_list.toList)
    bos.toByteArray
  }
}


trait ICommand{
  def execute(): Option[Any]
  
}

final case class WorldState(key:String, value:Any, other:Map[String,String] = null) //是否需要序列化方法？ 得换字典
//是否需要序列化方法？
abstract class BaseCommand(val api: Shim, val args: WorldState) extends ICommand with Serializable{
  var pos: Int = 0
  var result: Option[Any] = None
  var method: String = null

  def checkSame(target: BaseCommand):Boolean = {
    if(target.method.equals(this.method)  //命令类型相同
      && target.pos == this.pos  //命令位置相同
      && (target.args == this.args) //命令参数相同
      && (target.result == this.result))  //命令结果相同
      {
        System.out.println("~~~Play ok~~~")
        System.out.println("[record cmd] "+ target)
        System.out.println("[play   cmd] "+this)
        true
      }
    else{
        System.err.println("~~~Play error~~~")
        System.err.println("[record cmd] "+ target)
        System.err.println("[play   cmd] "+this)
        false
      }
  }
  override def toString():String ={
    ">>>>>>>>> pos:"+pos +" method:"+this.method+" args:"+args+ " result:"+ (if(result!=null)  result.toString() else null)
  }
}
class RecordSetVal(api: Shim, args: WorldState) extends BaseCommand(api, args){
  this.method = "setVal"
  def execute():Option[Any]={
    this.result = Option(api.setVal(args.key, args.value))
    return this.result
  }
}

class RecordGetVal(api: Shim, args: WorldState) extends BaseCommand(api, args){
  this.method = "getVal"
  def execute():Option[Any]={
    this.result=Option(api.getVal(args.key))
    return this.result
  }
}
class RecordSetState(api: Shim, args: WorldState) extends BaseCommand(api, args){
  this.method = "setState"
  def execute():Option[Any]={
    this.result = Option(api.setState(args.key, args.value.asInstanceOf[ShimRecord.Value]))
    return this.result
  }
}
class RecordGetState(api: Shim, args: WorldState) extends BaseCommand(api, args){
  this.method = "getState"
  def execute():Option[Any]={
    this.result = Option(api.getState(args.key))
    this.result
  }
}
class RecordGetStateEx(api: Shim, args: WorldState) extends BaseCommand(api, args){
  this.method = "getStateEx"
  def execute():Option[Any]={
    this.result = Option(api.getStateEx(args.other.get("cName").get, args.key))
    this.result
  }
}
class RecordbNodeCreditCode(api: Shim, args: WorldState) extends BaseCommand(api, args){
  this.method = "bNodeCreditCode"
  def execute():Option[Any]={
    this.result = Option(api.bNodeCreditCode(args.other.get("credit_code").get))
    this.result
  }
}

class ShimRecord(system: ActorSystem, cName: String) extends IShim{
    import Shim._
    import rep.storage.IdxPrefix._
    val shim =  new Shim(system, cName)
    //从交易传入, 内存中的worldState快照
    def sr = this.shim.sr
    def sr_=(some_sr:ImpDataPreload):Unit={
      this.shim.sr=some_sr
    }
    //记录状态修改日志
    def ol = this.shim.ol
    def ol_=(some_ol:scala.collection.mutable.ListBuffer[OperLog]):Unit={
      this.shim.ol=some_ol
    }
    
    var invoker: Invoker = new Invoker(this.shim) //以后需要改，目前是与ShimRecord生命周期相同，一个记录全部
    
    
    
    def setVal(key: Key, value: Any):Unit={
      val args = new WorldState(key,value)
      val cmd = new RecordSetVal(shim, args)
      this.invoker.pushCommand(cmd)
    }
    def getVal(key: Key):Any={
      val args = new WorldState(key,null)
      val cmd = new RecordGetVal(shim, args)
      this.invoker.pushCommand(cmd)
    }
    def setState(key: Key, value: Value): Unit={
      val args = new WorldState(key,value)
      val cmd = new RecordSetState(shim, args)
      this.invoker.pushCommand(cmd)
    } 
    def getState(key: Key): Value={
      val args = new WorldState(key,null)
      val cmd = new RecordGetState(shim, args)
      this.invoker.pushCommand(cmd).asInstanceOf[Value]
    } 
    
    def getStateEx(cName:String, key: Key):Value={
      val args = new WorldState(key,null, Map("cName"-> cName))
      val cmd = new RecordGetStateEx(shim, args)
      this.invoker.pushCommand(cmd).asInstanceOf[Value]
     
    }
    def bNodeCreditCode(credit_code: String) : Boolean={
      val args = new WorldState(null,null, Map("credit_code"-> credit_code))
      val cmd = new RecordbNodeCreditCode(shim, args)
      this.invoker.pushCommand(cmd).asInstanceOf[Boolean]
    }
}