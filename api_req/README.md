## RepChain1.0.0, API测试用例文件的介绍
***
#### 通过接口postTran，xml方式
1. 转账的测试用例(transfer/)   `Invoke`
   * assets6.xml assets6.json 正确转账
   * assets7.xml 目标账户不存在
   * assets8.xml 只能从本账户中转出
   * assets8.xml 自己给自己转账
2. 部署合约测试用例(deploy/)  `Deploy`
   * ContractAssetsTPL_deploy.xml 部署同一合约
   * **TODO** 合约升级的测试
3. 部署合约测试用例(changeState/)   `setState`
   * ContractAssetsTPL_changeState 禁用/启用合约
4. **TODO**  未完待续