package rep.api.rest

object ResultCode {

  val TranSizeExceed = 1001
  val ConsensusNodesNotEnough = 1002
  val TranIdDuplicate = 1003
  val NotValidInDebug = 1004
  val TranPoolFulled = 1005
  val TranParseError = 1006
  val SignatureVerifyFailed = 2001
  val UnkonwFailure = 5001
  val TransactionCheckedError = 5002

  val Transaction_Exception_In_Sandbox = 101
  val Transaction_Exception_In_SandboxOfScala = 102
  val Transaction_Exception_In_Preload = 103
  val Sandbox_Exception_In_Dispatch = 105
  val Sandbox_Success = 0

}
