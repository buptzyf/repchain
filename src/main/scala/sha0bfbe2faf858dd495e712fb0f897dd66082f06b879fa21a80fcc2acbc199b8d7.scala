import org.json4s._
import org.json4s.jackson.JsonMethods._
import rep.sc.contract._
import rep.storage.FakeStorage.Key

/**
 * 资产管理合约
 */

class sha0bfbe2faf858dd495e712fb0f897dd66082f06b879fa21a80fcc2acbc199b8d7 extends IContract{
  case class Transfer(from:String, to:String, amount:Int)
//  case class Proof(key:String, content:String)
  case class ReplaceCert(cert:String, addr:String)
//  case class Cert(cert:String, info:String)

  implicit val formats = DefaultFormats
  
    def init(ctx: ContractContext){      
      println(s"tid: $ctx.t.txid")
    }
    
    def set(ctx: ContractContext, data:Map[String,Int]):Object={
      println(s"set data:$data")
      for((k,v)<-data){
        ctx.api.setVal(k, v)
      }
      null
    }
    
        
    def read(ctx: ContractContext, key: String):Any={
      ctx.api.getVal(key)
    }
    
    def loadCert(ctx: ContractContext, cert: String): Unit = {
      	ctx.api.loadCert(cert);
	      print("cert:"+cert);
    }
    
    def write(ctx: ContractContext, data:Map[String,Int]):Object = {
       for((k,v)<-data){
        ctx.api.setVal(k, v)
      }
      null
    }
    
    def put_proof(ctx: ContractContext, data:Map[String,Any]):Object={
      //先检查该hash是否已经存在,如果已存在,抛异常
      for((k,v)<-data){
	      var pv0 = ctx.api.getVal(k)
	      if(pv0 != null)
		      throw new Exception("["+k+"]已存在，当前值["+pv0+"]");
	      ctx.api.setVal(k,v);
	      print("putProof:"+k+":"+v);
      }
	    "put_proof ok"
    }
    
    
    
    def signup(ctx: ContractContext, data:Map[String,String]):Object = {
      var addr = ""
    	for((k,v)<-data){
        ctx.api.check(ctx.t.cert.toStringUtf8,ctx.t)
    	  addr = ctx.api.signup(k,v)
      }
    	addr
    }
    
    def destroyCert(ctx: ContractContext, certAddr: String): Object = {
      println(s"destroy cert->addr:$certAddr")
      ctx.api.check(ctx.t.cert.toStringUtf8,ctx.t)    //ctx中自带交易内容
	    ctx.api.destroyCert(certAddr);
      "destory scuccess"
    }
    
    def replaceCert(ctx: ContractContext, data:ReplaceCert): Object = {
      val cert = data.cert
      val addr = data.addr
      ctx.api.check(ctx.t.cert.toStringUtf8,ctx.t)
	    ctx.api.replaceCert(cert,addr);   // 返回短地址
    }
    
    def transfer(ctx: ContractContext, data:Transfer):Object={
      val sfrom =  ctx.api.getVal(data.from)
      var dfrom =sfrom.toString.toInt
      if(dfrom < data.amount)
        throw new Exception("余额不足")
      var dto = ctx.api.getVal(data.to).toString.toInt
      //if(dto==null) dto = 0;
      
      ctx.api.setVal(data.from,dfrom - data.amount)
      ctx.api.setVal(data.to,dto + data.amount)
      "transfer ok"
    }
    
    /**
     * 根据action,找到对应的method，并将传入的json字符串parse为method需要的传入参数
     */
    def onAction(ctx: ContractContext,action:String, sdata:String ):Object={
      //println(s"onAction---")
      //return "transfer ok"
      val json = parse(sdata)
      
      action match {
        case "transfer" => 
          println(s"transfer oook")
          transfer(ctx,json.extract[Transfer])
        case "set" => 
          println(s"set") 
          set(ctx, json.extract[Map[String,Int]])
        case "put_proof" => 
          println(s"put_proof") 
          put_proof(ctx, json.extract[Map[String,Any]])
        case "signup" => 
          println(s"signup") 
          signup(ctx, json.extract[Map[String,String]])
        case "destroyCert" => 
          println(s"destroyCert")
          destroyCert(ctx, json.extract[String])
        case "replaceCert" => 
          println(s"replaceCert")
          replaceCert(ctx, json.extract[ReplaceCert])
      }
    }
    
}