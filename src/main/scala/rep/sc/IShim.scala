package rep.sc
import rep.log.RepLogger
import rep.storage.ImpDataPreload
import org.slf4j.Logger;
trait IShim {
 //应该一律用Option！来不及了先这样吧。

    type Key = String  
    type Value = Array[Byte]
 
    def setVal(key: Key, value: Any):Unit
    def getVal(key: Key):Any
    def setState(key: Key, value: Value): Unit 
    def getState(key: Key): Value 
    def getStateEx(cName:String, key: Key): Value
    def bNodeCreditCode(credit_code: String) : Boolean
    def getLogger:Logger={
      RepLogger.Business_Logger
    }
    
}