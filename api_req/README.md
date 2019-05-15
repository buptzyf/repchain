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

#### jmeter测试脚本
1. jmx/RepChain_Preview_API.jmx，包含了以下接口的测试用例
   * block，获得块数据：
     > /block/hash/{blockId} <br>
       /block/stream/{blockHeight} <br>
       /block/{blockHeight} 
   * chaininfo，查看块链信息：
     >/chaininfo
   * transaction，检索或提交交易：
     >/transaction/stream/{transactionId} <br>
      /transaction/postTranByString <br>
      /transaction/{transactionId} <br>
      /transaction/postTran <br>

2. 操作步骤
   * 打开jmeter导入jmx脚本
   * `ctrl + R` 执行线程组
3. 注意事项
   * jmeter测试脚本中的字符串提交交易的测试用例，其内置的字符串只能提交一次，因为txid是固定唯一的
   * /transaction/postTran只能在debug模式下使用，在jar部署模式下，该接口不能使用
4. **TODO**  未完待续