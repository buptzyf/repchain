package rep.network.consensus.asyncconsensus.config

import java.io.File
import rep.network.consensus.asyncconsensus.common.threshold.threshenc.tpke.{TPKEPrivateKey, TPKEPublicKey}
import rep.network.consensus.asyncconsensus.common.threshold.threshsig.boldyreva.{BLSPrivateKey, BLSPublicKey}

class ConfigOfDumbo(val nodeName:String) {

  private var thresholdPublicKeyOfTpke : TPKEPublicKey = null;
  private var thresholdPrivateKeyOfTpke : TPKEPrivateKey = null;
  private var thresdholdPublicKeyOfBlsIn13 : BLSPublicKey = null;
  private var thresdholdPrivateKeyOfBlsIn13 : BLSPrivateKey = null;
  private var thresdholdPublicKeyOfBlsIn23 : BLSPublicKey = null;
  private var thresdholdPrivateKeyOfBlsIn23 : BLSPrivateKey = null;

  loadKeys()

  private def loadKeys(): Unit ={
    val path = System.getProperty("user.dir") + File.separator + "jks"+File.separator+"threshold"+File.separator
    this.thresholdPublicKeyOfTpke = new TPKEPublicKey(path+"Threshold-TPKE-PublicKey-"+"2-3"+".key")
    this.thresdholdPublicKeyOfBlsIn13 = new BLSPublicKey(path+"Threshold-BLS-PublicKey-"+"1-3"+".key")
    this.thresdholdPublicKeyOfBlsIn23 = new BLSPublicKey(path+"Threshold-BLS-PublicKey-"+"2-3"+".key")
    this.thresholdPrivateKeyOfTpke = new TPKEPrivateKey(path+"Threshold-TPKE-PrivateKey-"+"2-3"+"-"+this.nodeName+".key")
    this.thresdholdPrivateKeyOfBlsIn13 = new BLSPrivateKey(path+"Threshold-BLS-PrivateKey-"+"1-3"+"-"+this.nodeName+".key")
    this.thresdholdPrivateKeyOfBlsIn23 = new BLSPrivateKey(path+"Threshold-BLS-PrivateKey-"+"2-3"+"-"+this.nodeName+".key")
  }

  def getEncryptPublicKey:TPKEPublicKey={
    this.thresholdPublicKeyOfTpke
  }

  def getEncryptPrivateKey:TPKEPrivateKey={
    this.thresholdPrivateKeyOfTpke
  }

  def getSignPublicKey13:BLSPublicKey={
    this.thresdholdPublicKeyOfBlsIn13
  }

  def getSignPublicKey23:BLSPublicKey={
    this.thresdholdPublicKeyOfBlsIn23
  }

  def getSignPrivateKey13:BLSPrivateKey={
    this.thresdholdPrivateKeyOfBlsIn13
  }

  def getSignPrivateKey23:BLSPrivateKey={
    this.thresdholdPrivateKeyOfBlsIn23
  }
}
