## RepChain1.0.0, 合约测试用例的介绍
***

1. 转账示例合约
    * ContractCertSpec：账户、证书管理合约的测试用例；
    * ContractTest：测试合约部署、修改合约状态和合约并发执，以及持久化交易到块中。
        > 1、同一内存快照中，不能部署同一个合约。<br>
          2、同一内存快照中，部署交易后（除state交易之外），即可调用。<br>
          3、同一内存快照中，改变合约状态，不影响合约调用。
2. 部署测试
    * DeploySpec：测试合约部署；
3. 合约状态修改测试
    * StateSpec：修改合约状态的测试（只能合约的部署者来操作）；
4. 供应链分账合约相关测试
    * SplitSpec：测试分账（对应供应链分账合约）；
    * SupplySpec：供应链分账合约的部署与分账测试，对应于SupplyTPL；
    * SupplySpec2：对应于SupplyTPL2与SupplyTPL3，供应链分账合约的版本升级测试；
5. 转账合约相关测试
    * TransferSpec：转账测试，对应于合约ContractAssetsTPL；
    * TransferSpec2：合约ContractAssetsTPL2串行测试
    * TransferSpec_Legal：合约ContractAssets_ Legal的测试

* **TODO**  未完待续...