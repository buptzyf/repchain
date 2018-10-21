package rep.trace.time


class blocktime{ 
  
  private var blocker :String = ""
  private var prehash :String = ""
  private var height :Long = 0l
  private var id : String = ""
  
  private var preblock_start = 0l
  private var preblock_end = 0l
  private var preload_start = 0l
  private var preload_end = 0l
  private var endorse_start = 0l
  private var endorse_end = 0l
  private var sendblock_start = 0l
  private var sendblock_end = 0l
  private var store_start = 0l
  private var store_end = 0l
  
  def blocktime(blocker:String,prehash:String,height:Long){
    this.blocker = blocker
    this.prehash = prehash
    this.height = height
    this.id = this.blocker + "_" + this.prehash+"_"+this.height
  }
  
  def writeBlockTime(identifier:String,flag:Int,value:Long)={
    if(this.id.equalsIgnoreCase(identifier)){
      flag match {      
          case timeType.preblock_start  =>    this.preblock_start = value
          case timeType.preblock_end =>       this.preblock_end = value
          case timeType.preload_start =>      this.preload_start = value
          case timeType.preload_end  =>       this.preload_end = value
          case timeType.endorse_start =>      this.endorse_start = value
          case timeType.endorse_end =>        this.endorse_end = value
          case timeType.sendblock_start =>    this.sendblock_start = value
          case timeType.sendblock_end  =>     this.sendblock_end = value
          case timeType.store_start =>        this.store_start = value
          case timeType.store_end =>          this.store_end = value
          case _ =>
      }
    }
  }
  
  
  
}