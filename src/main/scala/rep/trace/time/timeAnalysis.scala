package rep.trace.time

import scala.collection.mutable.ArrayBuffer
import org.slf4j.LoggerFactory

object timeAnalysis {
    protected def log = LoggerFactory.getLogger(this.getClass)
  
    val  count = 100
    val  outType = 1 //1=print 0=log
    var  q : ArrayBuffer[blocktime]  = new ArrayBuffer[blocktime]()
    var  BlockerStart : Long = 0l
    var  BlockerEnd :Long = 0l
    
    def  addBlockTime(bt:blocktime,blockertag:String)={
       this.synchronized{
         if(blockertag.equals(bt.getblocker)){
           if(q.size == 0) this.BlockerStart = System.currentTimeMillis()
           this.q += bt
           if(q.size > count){
             BlockerEnd = System.currentTimeMillis()
             val sb = timeToString
             if(this.outType == 1){
               this.printTime(sb)
             }else if(this.outType == 1){
               this.printTimeToLog(sb)
             }
             this.q.clear()
           }
         }
       }
    }
    
    
    private def printTime(sb:String)={
      println(s"####current 1000 blocks,spent time=${this.BlockerEnd-this.BlockerStart}")
       println(sb)
    }
    
    private def printTimeToLog(sb:String)={
      log.info(s"####current 1000 blocks,spent time=${this.BlockerEnd-this.BlockerStart}")
      log.info(sb)
    }
    
    private def timeToString:String={
      var sb = new StringBuffer()
      this.q.foreach(f=>{
        sb.append(f.WriteToString).append("\n")
      })
      sb.toString()
    }
    
}