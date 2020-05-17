package rep.authority





object MsgTypeOfVerify  extends Enumeration {
  type MsgTypeOfVerify = Value

  val Success = Value(0,"Success")  //验证成功
  val CertInvalid = Value(1,"CertInvalid")  //证书无效
  val NotChainCert = Value(2,"NotChainCert") //不是链证书
  val ForbidedOfOperate = Value(3,"ForbidOfOperate")//操作被禁止
  val ForbidedOfContract = Value(4,"ForbidOfContract")//合约被禁止
  val OperateInvalid = Value(5,"OperateInvalid")//操作无效
  val ForbidedOfSigner = Value(6,"ForbidedOfSigner")//签名者被禁止
  val abolishedOfAuth = Value(7,"abolishedOfAuth")//授权被取消
  val NotPermission = Value(0,"NotPermission")  //验证成功

}
