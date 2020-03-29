package rep.sc.tpl.stainless

import stainless.lang._
import stainless.annotation._
import scala.annotation.meta.field

import rep.sc.scalax.IContract
import rep.sc.scalax.ContractContext
import rep.sc.Shim

case class ContractContextWrapper[K, V](
  @extern
  ctx: rep.sc.scalax.ContractContext
) {

  @extern
  def setVal(key: String, value: Int): Unit = {
    ctx.api.setVal(key, value)
  } ensuring {
    this.contains(key) &&
    this.getVal(key) == value
  }

  @extern @pure
  def getVal(key: rep.sc.Shim.Key): Any = {
    // require(contains(key))
    ctx.api.getVal(key)
  }

  @extern @pure
  def contains(key: String): Boolean = {
    ctx.api.getVal(key) != null
  }

}