package rep.network.module.cfrd


/**
 * Created by jiangbuyun on 2020/03/15.
 * CFRD管理的actor
 */
object CFRDActorType {
  //cfrd共识模式的actor类型的注册，关键字以20开头
  case object ActorType{
    val blocker = 201
    val voter = 202
    val endorsementcollectioner = 203

    val confirmerofblock = 204
    val dispatchofRecvendorsement = 205
    val gensisblock = 206

    val synchrequester = 207
    val synchresponser = 208
  }
}
