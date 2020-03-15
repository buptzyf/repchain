package rep.network.module

object ModuleActorType {

  //系统的actor类型的注册，关键字以10开头
  case object ActorType {
    //外部API服务actor
    val webapi = 101
    //自动（测试时采用）产生交易的actor
    val peerhelper = 102
    //分派交易预执行actor
    val dispatchofpreload = 103
    //出块时分派交易预执行actor
    val transactiondispatcher = 104
    //交易缓存池actor，接收广播交易
    val transactionpool = 105
    //区块存储actor，将确认的块持久化到磁盘
    val storager = 106
    //
    val modulemanager = 107
  }

}
