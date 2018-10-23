package rep.trace.time


class blocktime(blocker:String,prehash:String,height:Long){ 
  
  //private var blocker :String = ""
  //private var prehash :String = ""
  //private var height :Long = 0l
  private var id : String = ""
  this.id = this.blocker + "_" + this.prehash+"_"+this.height
  
  private var preblock_start = 0l
  private var preblock_end = 0l
  private var preload_start = 0l
  private var preload_recv_start = 0l
  private var preload_recv_end = 0l
  private var preload_end = 0l
  private var endorse_start = 0l
  private var endorse_recv_start = 0l
  private var endorse_recv_end = 0l
  private var endorse_end = 0l
  private var sendblock_start = 0l
  private var sendblock_end = 0l
  private var store_start = 0l
  private var store_end = 0l
  
  /*def blocktime(blocker:String,prehash:String,height:Long){
    this.blocker = blocker
    this.prehash = prehash
    this.height = height
    this.id = this.blocker + "_" + this.prehash+"_"+this.height
  }*/
  
  def getblocker:String={
    this.blocker
  }
  
  def getId:String={
    this.id
  }
  
  def writeBlockTime(identifier:String,flag:Int,value:Long)={
    if(this.id.equalsIgnoreCase(identifier)){
      flag match {      
          case timeType.preblock_start  =>    this.preblock_start = value
          case timeType.preblock_end =>       this.preblock_end = value
          case timeType.preload_start =>      this.preload_start = value
          case timeType.preload_recv_start =>    this.preload_recv_start = value
          case timeType.preload_recv_end =>      this.preload_recv_end = value
          case timeType.preload_end  =>       this.preload_end = value
          case timeType.endorse_start =>      this.endorse_start = value
          case timeType.endorse_recv_start  =>       this.endorse_recv_start = value
          case timeType.endorse_recv_end =>      this.endorse_recv_end = value
          case timeType.endorse_end =>        this.endorse_end = value
          case timeType.sendblock_start =>    this.sendblock_start = value
          case timeType.sendblock_end  =>     this.sendblock_end = value
          case timeType.store_start =>        this.store_start = value
          case timeType.store_end =>          this.store_end = value
          case _ =>
      }
    }
  }
  
  def WriteToString:String={
    var sb = new StringBuffer()
    sb.append(s"--identifier=${this.id},preblock-time=${this.preblock_end-this.preblock_start}"+
                  s",preload-time to recv =${this.preload_recv_start-this.preload_start}"+
                  s",preload-time for exe =${this.preload_recv_end-this.preload_recv_start}"+
                  s",preload-time to finish =${this.preload_end-this.preload_recv_end}"+
                  s",endorse-time to recv=${this.endorse_recv_start-this.endorse_start}"+
                  s",endorse-time for exe =${this.endorse_recv_end-this.endorse_recv_start}"+
                  s",endorse-time to finish=${this.endorse_end-this.endorse_recv_end}"+
                  s",sendblock-time=${this.sendblock_end-this.sendblock_start}"+
                  s",storeblock-time=${this.store_end-this.store_start}"
              )
    sb.append("\r")
    sb.append(s"++identifier=${this.id},preblock_start=${this.preblock_start}"+
                  s",preblock_end=${this.preblock_end}"+
                  s",preload_start=${this.preload_start}"+
                  s",preload_recv_start=${this.preload_recv_start}"+
                  s",preload_recv_end=${this.preload_recv_end}"+
                  s",preload_end=${this.preload_end}"+
                  s",endorse_start=${this.endorse_start}"+
                  s",endorse_recv_start=${this.endorse_recv_start}"+
                  s",endorse_recv_end=${this.endorse_recv_end}"+
                  s",endorse_end=${this.endorse_end}"+
                  s",sendblock_start=${this.sendblock_start}"+
                  s",sendblock_end=${this.sendblock_end}"+
                  s",store_start=${this.store_start}"+
                  s",store_end=${this.store_end}"
              )
    sb.toString()
  }
  
}