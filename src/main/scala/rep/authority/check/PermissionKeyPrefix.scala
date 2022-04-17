package rep.authority.check

import rep.app.conf.SystemProfile
import rep.sc.tpl.did.DidTplPrefix

object PermissionKeyPrefix {
  val prefix = IdxPrefix.WorldStateKeyPreFix + SystemProfile.getAccountChaincodeName + "_"
  val opPrefix = prefix + DidTplPrefix.operPrefix
  val sigPrefix = prefix + DidTplPrefix.signerPrefix
  val authPrefix = prefix + DidTplPrefix.authPrefix
  val certPrefix = prefix + DidTplPrefix.certPrefix
  val certHashPrefix = prefix + DidTplPrefix.hashPrefix
  val authBindCertPrefix = prefix + DidTplPrefix.bindPrefix
}
