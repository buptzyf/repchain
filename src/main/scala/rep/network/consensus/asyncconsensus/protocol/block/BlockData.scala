package rep.network.consensus.asyncconsensus.protocol.block


import rep.network.consensus.asyncconsensus.common.crypto.ThesholdEncryptAPI
import rep.network.consensus.asyncconsensus.config.ConfigOfManager
import rep.network.consensus.asyncconsensus.protocol.block.AsyncBlock.TPKEShare
import scala.util.control.Breaks._
import scala.collection.immutable.HashMap
import scala.collection.mutable.ArrayBuffer

class BlockData(nodeName:String,round:String) {
  private val cfg = ConfigOfManager.getManager.getConfig(nodeName)
  private var isAddToVCS  = false
  private var cipher:HashMap[String,ThesholdEncryptAPI] = new HashMap[String,ThesholdEncryptAPI]
  private var context:HashMap[String,Array[Byte]] = new HashMap[String,Array[Byte]]

  private var notHandleMessage = new ArrayBuffer[TPKEShare]()

  def getRound:String={this.round}

  def addToMsg(msg:TPKEShare):Unit={
    this.notHandleMessage.append(msg)
  }

  def getMsg:ArrayBuffer[TPKEShare]={
    this.notHandleMessage
  }

  def delMsg(i:Int):Unit={
    this.notHandleMessage.remove(i)
  }

  def getIsAddToVCS:Boolean={
    this.isAddToVCS
  }

  def setIsAddToVCS:Unit={
    this.isAddToVCS = true
  }

  def encrypt(data:Array[Byte]):String={
    val tool = new ThesholdEncryptAPI(cfg.getEncryptPublicKey,cfg.getEncryptPrivateKey)
    tool.encrypt(data)
  }

  def recvCipher(rootHash:String,cipherText:String):Boolean={
    var result = true
    if(!this.cipher.contains(rootHash)){
      val tool = new ThesholdEncryptAPI(cfg.getEncryptPublicKey,cfg.getEncryptPrivateKey)
      if(tool.recvCipherInfo(cipherText)){
        this.cipher += rootHash -> tool
      }else{
        result = false
      }
    }
    result
  }

  def recvShareCipher(rootHash:String,shareDecrypt:String):Boolean={
    var result = false
    if(this.cipher.contains(rootHash)){
      var tool = cipher.getOrElse(rootHash,null)
      if(tool != null){
        if(tool.recvDecryptShare(shareDecrypt)){
          this.cipher += rootHash -> tool
          result = true
        }
      }
    }
    result
  }

  def shareDencrypt(rootHash:String):String={
    var result = ""
    if(this.cipher.contains(rootHash)){
      var tool = cipher.getOrElse(rootHash,null)
      if(tool != null){
        result = tool.decryptShare()
        this.cipher += rootHash -> tool
      }
    }
    result
  }

  def hasDencrypt(rootHash:String):Boolean={
    var result = false
    if(this.cipher.contains(rootHash)){
      val tool = this.cipher.getOrElse(rootHash,null)
      if(tool != null){
        result = tool.hasDecrypt
      }
    }
    result
  }

  def dencrypt(rootHash:String):Array[Byte]={
    var result : Array[Byte] = null
    if(this.cipher.contains(rootHash)){
      val tool = this.cipher.getOrElse(rootHash,null)
      if(tool != null){
        result = tool.decrypt()
      }
    }
    result
  }

  def setContext(rootHash:String,data:Array[Byte]):Unit={
    this.context += rootHash -> data
  }

  def hasContext(rootHash:String):Boolean={
    this.context.contains(rootHash)
  }

  def getAllContext:HashMap[String,Array[Byte]]={
    this.context
  }

  def hasDencryptFinish:Boolean={
    var result = true
    breakable(
    this.cipher.foreach(c=>{
      if(!this.context.contains(c._1)){
        result = false
        break()
      }
    })
    )
    result
  }
}
