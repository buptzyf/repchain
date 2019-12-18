package rep.storage.test

import rep.storage.IdxPrefix.WorldStateKeyPreFix
import rep.storage.ImpDataAccess

//import scala.collection.immutable._

import rep.utils.SerializeUtils.serialise
import rep.utils.SerializeUtils.deserialise

object modifyWorldState extends App {
  val da1 = ImpDataAccess.GetDataAccess("121000005l35120456.node1")
  val preKey = WorldStateKeyPreFix + "ContractAssetsTPL" + "_"
  val key = "121000005l35120456"
  val value = 3
  da1.Put(preKey + key, serialise(value))
  println(deserialise(da1.Get(preKey + key)))
}