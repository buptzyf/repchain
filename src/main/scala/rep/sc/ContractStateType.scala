package rep.sc


/** 定义合约的状态类型
   *  1.ContractInLevelDB说明合约的状态是存在于LevelDB中，合约已经被持久化了。
			2.ContractInSnapshot 说明合约的状态是存在于快照中，合约尚未持久化。
			3.ContractInNone 说明合约尚未初始化，表示合约分派器属于刚启动。
   */
object ContractStateType  extends Enumeration {
    type ContractStateType = Value
    
    val ContractInLevelDB = Value(1,"ContractInLevelDB")
    val ContractInSnapshot = Value(2,"ContractInSnapshot")
    val ContractInNone = Value(3,"ContractInNone")
  
}