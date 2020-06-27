package rep.authority.check

import rep.app.conf.SystemProfile
import rep.sc.tpl.did.DidTplPrefix
import rep.storage.IdxPrefix

object PermissionKeyPrefix {
  val prefix = IdxPrefix.WorldStateKeyPreFix + SystemProfile.getAccountChaincodeName + "_"
  val opPrefix = prefix + DidTplPrefix.operPrefix
  val sigPrefix = prefix + DidTplPrefix.signerPrefix
  val authPrefix = prefix + DidTplPrefix.authPrefix
}
