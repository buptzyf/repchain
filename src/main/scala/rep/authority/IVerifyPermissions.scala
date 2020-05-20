package rep.authority

import rep.authority.MsgOfVerifyPermission.ResultOfVerify

trait IVerifyPermissions{
  //验证服务的权限
  /**
   * 参数列表：
   * operate_endpoint //服务器地址
   * operate_service_name//服务名
   * cert_hash//证书的sha256
   * */
  def VerifyOfService(operate_endpoint:String,operate_service_name:String,cert_hash:String) : ResultOfVerify

  //验证合约的权限
  /***
   *
   * 参数列表：
   * operate_endpoint//服务器地址
   * operate_service_name//服务名
   * auth_full_name//权限约束内容
   * certid  //包含RDID和证书名称
   */

  def VerifyOfContract(operate_endpoint:String,operate_service_name:String,auth_full_name:String,certid:String):ResultOfVerify
}
