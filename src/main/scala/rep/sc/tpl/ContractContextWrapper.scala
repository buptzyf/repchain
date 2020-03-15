package rep.sc.tpl

import stainless.lang._
import stainless.annotation._
import scala.annotation.meta.field

import rep.sc.scalax.IContract
import rep.sc.scalax.ContractContext

case class ContractContextWrapper[K, V](
  @extern
  ctx: ContractContext
) {

  @extern
  def setVal(key: Key, value: Any): Unit = {
    ctx.api.setVal(key, value)
  } ensuring {
    this.contains(key) &&
    this.getVal(key) == value
  }

  @extern @pure
  def getVal(key: Key): Any = {
    // require(contains(key))
    ctx.api.getVal(key)
  }

  @extern @pure
  def contains(key: Key): Boolean = {
    ctx.api.getVal(pid) != null
  }

}