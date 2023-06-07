package rep.accumulator.verkle

object VerkleTreeType extends Enumeration {
  type VerkleTreeType = Value

  val WorldStateTree = Value(1, "WorldStateTree")
  val TransactionTree = Value(2, "TransactionTree")
  val TransactionResultTree = Value(3, "TransactionResultTree")
}
