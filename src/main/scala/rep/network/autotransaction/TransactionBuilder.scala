package rep.network.autotransaction

import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
import rep.crypto.cert.SignTool
import rep.proto.rc2.{ChaincodeDeploy, ChaincodeId, ChaincodeInput, Signature, Transaction}
import rep.utils.{IdTool, TimeUtils}

class TransactionBuilder(signTool: SignTool) {
  /**
   * 采用节点私钥创建交易的方法
   *
   */
  def createTransaction4Invoke(nodeName: String, chaincodeId: ChaincodeId,
                               chaincodeInputFunc: String, params: Seq[String]): Transaction = {
    createTransaction4Invoke(nodeName, chaincodeId, chaincodeInputFunc, params, 0,"")
  }

  def createTransaction4Invoke(nodeName: String, chaincodeId: ChaincodeId,
                               chaincodeInputFunc: String, params: Seq[String],
                               gasLimited:Int,oid:String): Transaction = {
    //create transaction
    var t: Transaction = new Transaction()
    val millis = TimeUtils.getCurrentTime()
    if (chaincodeId == null) t

    //create transaction Id
    val txid = IdTool.getRandomUUID
    //create transaction input
    val cip = new ChaincodeInput(chaincodeInputFunc, params)
    //add transaction id
    t = t.withId(txid)
    //add chaincode
    t = t.withCid(chaincodeId)
    //add transaction input
    t = t.withIpt(cip)
    ////add transaction type
    t = t.withType(rep.proto.rc2.Transaction.Type.CHAINCODE_INVOKE)
    //add transaction gas,default 0=not limit
    t = t.withGasLimit(gasLimited)
    //add transaction instance name
    t = t.withOid(oid)
    //clean signature
    t = t.clearSignature
    //use node's key sign transaction
    val certid = IdTool.getCertIdFromName(nodeName)
    var sobj = Signature(Option(certid), Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)))
    sobj = sobj.withSignature(ByteString.copyFrom(this.signTool.sign(nodeName, t.toByteArray)))
    //add signature
    t = t.withSignature(sobj)
    //finsih
    t
  }

  def createTransaction4Deploy(nodeName: String, chaincodeId: ChaincodeId,
                               spcPackage: String, legal_prose: String, timeout: Int,
                               ctype: rep.proto.rc2.ChaincodeDeploy.CodeType): Transaction = {
    createTransaction4Deploy(nodeName, chaincodeId,
      spcPackage, legal_prose, timeout: Int, ctype,
      rep.proto.rc2.ChaincodeDeploy.RunType.RUN_SERIAL,//default RUN_SERIAL
      rep.proto.rc2.ChaincodeDeploy.StateType.STATE_BLOCK,//default STATE_BLOCK
      rep.proto.rc2.ChaincodeDeploy.ContractClassification.CONTRACT_CUSTOM,//default CONTRACT_CUSTOM
      0)
  }

  def createTransaction4Deploy(nodeName: String, chaincodeId: ChaincodeId,
                               spcPackage: String, legal_prose: String, timeout: Int,
                               ctype: rep.proto.rc2.ChaincodeDeploy.CodeType,
                               rtype: rep.proto.rc2.ChaincodeDeploy.RunType,//default RUN_SERIAL
                               stype: rep.proto.rc2.ChaincodeDeploy.StateType,//default STATE_BLOCK
                               cclassfiction:rep.proto.rc2.ChaincodeDeploy.ContractClassification,//default CONTRACT_CUSTOM
                               gasLimited:Int): Transaction = {
    var t: Transaction = new Transaction()
    val millis = TimeUtils.getCurrentTime()
    if (chaincodeId == null) t

    val txid = IdTool.getRandomUUID
    //*************create deploy content************************
    var cip = new ChaincodeDeploy(timeout)
    //add contract code
    cip = cip.withCodePackage(spcPackage)
    //add LegalProse
    cip = cip.withLegalProse(legal_prose)
    //add code language
    cip = cip.withCType(ctype)
    //add run type
    cip = cip.withRType(rtype)
    //add worldState member proof type
    cip = cip.withSType(stype)
    //add contract level
    cip = cip.withCclassification(cclassfiction)
    //*************create deploy content************************

    //*************create transaction content************************
    //add transaction id
    t = t.withId(txid)
    //add chaincode id
    t = t.withCid(chaincodeId)
    //***add deploy content***
    t = t.withSpec(cip)
    //add transaction type
    t = t.withType(rep.proto.rc2.Transaction.Type.CHAINCODE_DEPLOY)
    //add transaction gas,default 0=not limit
    t = t.withGasLimit(gasLimited)
    //clean signature
    t = t.clearSignature
    //use node's key sign transaction
    val certid = IdTool.getCertIdFromName(nodeName)
    var sobj = Signature(Option(certid), Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)))
    sobj = sobj.withSignature(ByteString.copyFrom(this.signTool.sign(nodeName, t.toByteArray)))
    //add transaction's signature
    t = t.withSignature(sobj)
    //finish
    t
  }

  def createTransaction4State(nodeName: String, chaincodeId: ChaincodeId,
                              state:Boolean): Transaction = {
    createTransaction4State(nodeName, chaincodeId,
      state,0)
  }

  def createTransaction4State(nodeName: String, chaincodeId: ChaincodeId,
                              state:Boolean,gasLimited:Int): Transaction = {
    //create transaction
    var t: Transaction = new Transaction()
    val millis = TimeUtils.getCurrentTime()
    if (chaincodeId == null) t

    //create transaction Id
    val txid = IdTool.getRandomUUID
    //add transaction id
    t = t.withId(txid)
    //add chaincode
    t = t.withCid(chaincodeId)

    //add transaction type
    t = t.withType(rep.proto.rc2.Transaction.Type.CHAINCODE_SET_STATE)
    //add contract state
    t = t.withState(state)
    t = t.withGasLimit(gasLimited)
    //clean signature
    t = t.clearSignature
    //use node's key sign transaction
    val certid = IdTool.getCertIdFromName(nodeName)
    var sobj = Signature(Option(certid), Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)))
    sobj = sobj.withSignature(ByteString.copyFrom(this.signTool.sign(nodeName, t.toByteArray)))
    //add transaction's signature
    t = t.withSignature(sobj)
    //finish
    t
  }
}
