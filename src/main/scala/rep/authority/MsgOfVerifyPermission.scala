package rep.authority

import rep.authority.MsgTypeOfVerify.MsgTypeOfVerify

object MsgOfVerifyPermission {
  case class ResultOfVerify(result:Boolean,msg:MsgTypeOfVerify)
}
