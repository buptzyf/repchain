package rep.network.module


/**
 * Created by jiangbuyun on 2020/03/15.
 * 共识模块中管理的公共的actor
 */
object ModuleActorType {

  //系统的actor类型的注册，关键字以10开头
  case object ActorType {
    //外部API服务actor
    val webapi = 101
    //自动（测试时采用）产生交易的actor
    val peerhelper = 102
    //分派交易预执行actor
    val dispatchofpreload = 103
    val dispatchofpreloadinstream = 7002
    //出块时分派交易预执行actor
    val transactiondispatcher = 104
    //交易缓存池actor，接收广播交易
    val transactionpool = 105
    //区块存储actor，将确认的块持久化到磁盘
    val storager = 106
    //
    val modulemanager = 107
    //交易收集actor，接收广播交易，代替transactionpool
    val transactioncollectioner = 108

    val transactionPreloadInStream = 901

    val  syncSaveBlock = 109

    //val memberlistener = 801
  }

}
